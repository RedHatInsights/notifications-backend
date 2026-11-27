# Aggregator Scheduling Guidelines

## Overview and Architecture

The `aggregator` module runs periodic scheduled jobs (`NotificationsApp`, typically every 15 minutes) that aggregate platform events into email digests (daily and weekly) and emit `AggregationCommand` CloudEvents to Kafka (`egress`). Times round to the nearest 15-minute slot boundary (`AggregationTimeHelper.computeReferenceTime()`).

- **`AbstractEmailAggregationJob`**: Base template managing run lifecycle: cluster checks, default config provisioning, atomic org claiming, Kafka emission, state transitions, and quiet-period finalization.
- **`WeeklyEmailAggregationJob` / `DailyEmailAggregationJob`**: Weekly uses optimistic claiming with `DigestTriggerOrgConfigRepository`; daily uses legacy `AggregationOrgConfigRepository`.
- **`DigestCronUtils`**: Parses cron expressions, calculates execution slots, and resolves window boundaries.

---

## 1. State Machine Lifecycle & Emit-Before-Persist

State per org and subscription type is tracked in `digest_trigger_org_config.status`:
- `PENDING`: Idle state awaiting execution (`next_run <= executionTime`).
- `PROCESSING`: Claimed by an active aggregator pod (`claimed_at` is set).

There is no permanent `COMPLETED` state; all executions return to `PENDING`.
Transitions:
1. **Claiming**: Atomically claim due orgs (`status = 'PENDING' AND next_run <= :now`) -> `PROCESSING`, setting `claimed_at = :now`.
2. **Success**: Reset to `PENDING`, `claimed_at = NULL`, advance `last_run` to slot time, advance `next_run` to next cron interval.
3. **Failure**: Reset to `PENDING`, `claimed_at = NULL`, preserve `last_run`, reschedule `next_run` to tomorrow (`now.plusDays(1)`).
4. **Zero Events (Quiet)**: Reset to `PENDING`, `claimed_at = NULL`, advance both `last_run` and `next_run`.
5. **Crash Recovery**: Stale `PROCESSING` rows reset to `PENDING` via `resetStaleClaims()`.

**Emit-Before-Persist (At-Least-Once Delivery)**:
Messages are emitted to Kafka before database status is updated. If a crash or timeout occurs after emission, stale claim recovery or retry will re-emit. Downstream consumers must process `AggregationCommand` messages idempotently. Never invert this order, as failing during emission would drop digests without retry.

---

## 2. Universal next_run Advancement (`finalizeRun`)

`next_run` **must advance for every claimed organization**, even those with zero events. If quiet orgs are not updated, they remain `next_run <= now` and are re-claimed every 15 minutes, causing severe redundant database load.

`AbstractEmailAggregationJob` enforces this by calling `finalizeRun()`:
```java
List<String> noEventsOrgIds = claimedOrgIds.stream()
    .filter(id -> !allUpdatedOrgIds.contains(id))
    .collect(Collectors.toList());
digestTriggerOrgConfigRepository.advanceNextRunForOrgsWithNoEvents(executionTime, noEventsOrgIds);
```
This advances `last_run` and `next_run`, resetting `status = 'PENDING'` and `claimed_at = NULL` for quiet orgs.

---

## 3. last_run Null Handling in New Configurations

New configurations (from batch provisioning or user preferences) have `last_run = NULL`. Code must never dereference `last_run` without null handling:

1. **Aggregation Queries**: Coalesce `last_run` with default lookback (7 days for weekly):
   ```sql
   AND ev.created > COALESCE(dtc.lastRun, :oneWeekAgo) AND ev.created <= :now
   ```
   *Anti-pattern*: Using `AND ev.created > dtc.lastRun` drops all events when `lastRun` is null. Combining bounds with `AND` drops events during retry delays.
2. **Command Window**: For first runs (`lastRun == null`), use `[now.minusDays(7), now]`. For subsequent runs, use `[lastRun, calculateCurrentSlot(cron, now)]`.
3. **Updates**: Set `newLastRun = config.getLastRun() == null ? now : calculateCurrentSlot(cron, now)`.

---

## 4. Event Window Calculation and Schedule Changes

Always anchor the window end to the scheduled slot time using `calculateCurrentSlot(cronExpression, now)` rather than computing from previous run (`calculateNextRun(cron, lastRun)`):
- **Schedule Changes**: If an org changes schedule (e.g., Monday to Wednesday), calculating from `lastRun` evaluates to a past slot, permanently lagging future digests. `calculateCurrentSlot` evaluates to the current slot, creating an extended transition window with **zero dropped events**.
- **Retry Consistency**: If a Monday job fails and runs on Tuesday, `calculateCurrentSlot` still returns Monday's slot, preventing drift.

---

## 5. Concurrency Model and Stale Claims

Aggregator pods run concurrently across multi-pod deployments:

### Atomic Claiming & Pod Isolation
- **Atomic Claim**: Pods claim orgs using `UPDATE digest_trigger_org_config SET status = 'PROCESSING', claimed_at = :now WHERE status = 'PENDING' AND next_run <= :now RETURNING org_id;`. Row locks ensure disjoint sets of orgs across pods.
- **Pod Scoping**: Subsequent queries must filter by `IN :claimedOrgIds`. Never query `WHERE status = 'PROCESSING'` without scoping to claimed IDs, which causes race conditions and duplicate processing across pods.

### Stale-Claim Recovery
`resetStaleClaims()` runs at cycle start to recover orphaned claims (threshold defaults to 6h):
```sql
UPDATE DigestTriggerOrgConfig SET status = 'PENDING', claimed_at = NULL
WHERE status = 'PROCESSING' AND id.subscriptionType = :type
  AND (claimed_at < :threshold OR (claimed_at IS NULL AND nextRun < :threshold))
```
**Invariant**: Staleness must compare against `claimed_at`, **never** `next_run`. If a job is delayed by >threshold, due orgs have `next_run` in the past. Checking `next_run` would immediately reset newly claimed rows while active, causing duplicate runs.

---

## 6. Query Parameter Limits (65,535 Ceiling)

PostgreSQL JDBC enforces a hard limit of 65,535 bind parameters. Any `IN (:orgIds)` clause with large collections will fail.
- Always partition ID collections using `IN_CLAUSE_MAX = 32000` (leaving headroom for other parameters):
```java
private static <T> List<List<T>> partition(List<T> list, int batchSize) {
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
        batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return batches;
}
```
Apply partitioning in all bulk repository operations (`getApplicationsWithPendingWeeklyAggregation`, `updateLastAndNextRunForWeekly`, `advanceNextRunForOrgsWithNoEvents`, `updateNextRunForFailedWeekly`).

---

## 7. Feature Flags & Cluster Coordination

- **Unleash Toggles**: Protect aggregation pipelines with Unleash flags (`notifications-aggregator-suspend-weekly-aggregation` for emergency shutdown). Always provide fail-safe defaults when Unleash is unavailable.
- **Active-Standby Coordination**: To prevent concurrent executions across clusters:
  ```java
  private boolean shouldRunOnThisCluster() {
      Optional<String> myCluster = aggregatorConfig.getClusterId();
      if (myCluster.isEmpty()) {
          return true; // Backward compatibility
      }
      Optional<String> activeCluster = aggregatorConfig.getActiveCluster();
      if (activeCluster.isEmpty()) {
          return false; // Fail-safe: do not run if Unleash is unreachable
      }
      return myCluster.get().equals(activeCluster.get());
  }
  ```
  Standby pods skip execution and log via `"%s aggregator run skipped on %s cluster due to Unleash flag"`.

---

## Summary Checklist for Aggregator Scheduling PRs

- [ ] **State Machine**: Every path transitions claimed rows from `PROCESSING` back to `PENDING`.
- [ ] **Advance Quiet Orgs**: `next_run` advances for orgs with zero events (`finalizeRun`).
- [ ] **Null `last_run`**: Handled via `COALESCE(dtc.lastRun, :fallback)` and `[now - 7d, now]` window.
- [ ] **Event Window**: Computed via `calculateCurrentSlot(cron, now)`, not `calculateNextRun(cron, lastRun)`.
- [ ] **Pod Isolation**: Queries filter by `IN :claimedOrgIds`, never global `status = 'PROCESSING'`.
- [ ] **Stale Claims**: Staleness checked against `claimed_at < threshold`, never `next_run`.
- [ ] **Parameter Limits**: Collections partitioned with `IN_CLAUSE_MAX <= 32000`.
- [ ] **Feature Flags & Cluster**: Unleash gating with safe fallbacks; cluster active check verified.
- [ ] **Idempotency**: Downstream processing tolerates duplicate commands from emit-before-persist.
