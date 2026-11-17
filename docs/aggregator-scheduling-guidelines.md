# Aggregator Scheduling Guidelines

## Overview and Architecture

The `aggregator` module runs periodic scheduled jobs that aggregate platform events into email digests (daily and weekly). It runs as a standalone Quarkus application (`NotificationsApp`) triggered periodically (typically every 15 minutes by a Kubernetes CronJob or deployment scheduler).

Each run rounds the current time to the nearest 15-minute slot boundary (`AggregationTimeHelper.computeReferenceTime()`). The job discovers organizations due for aggregation, computes their event time windows, queries aggregated events, builds `AggregationCommand` payloads, emits CloudEvents to Kafka (`egress` channel), and advances scheduling metadata.

### Core Components

- **`NotificationsApp`**: Main entrypoint implementing `QuarkusApplication`. Coordinates job execution, handles cluster active/standby checks, manages Prometheus metrics export, and executes daily and weekly jobs sequentially.
- **`AbstractEmailAggregationJob`**: Base template-method class managing the lifecycle of an aggregation run: cluster coordination check, metric timers, default config provisioning, atomic org claiming, command generation, Kafka emission, state transitions, and error handling.
- **`WeeklyEmailAggregationJob` / `DailyEmailAggregationJob`**: Job implementations for weekly and daily aggregation. Weekly uses optimistic claiming with `DigestTriggerOrgConfigRepository`, while daily uses legacy aggregation tables (`AggregationOrgConfigRepository`).
- **`DigestTriggerOrgConfigRepository`**: Repository for managing `DigestTriggerOrgConfig` entities, atomic state transitions, stale claim recovery, and aggregation query execution.
- **`DigestCronUtils`**: Shared utility for parsing cron expressions, computing next execution slots, and resolving event window boundaries.

---

## 1. Scheduling State Machine Lifecycle

The weekly aggregation subsystem tracks scheduling and execution state per organization and subscription type in the `digest_trigger_org_config` table.

### State Representation

The scheduling state machine has two operational states represented by the `status` column:

| Status | Description |
|---|---|
| `PENDING` | Default idle state. The organization is awaiting execution when `next_run <= executionTime`. |
| `PROCESSING` | Active state. The organization has been claimed by an aggregator pod for processing. |

There is no permanent `COMPLETED` state. When processing concludes (success, failure, or zero events), the organization transitions back to `PENDING` with an updated `next_run` timestamp.

### State Transitions

```
                    +------------------------------------+
                    |                                    |
                    v                                    |
             [ PENDING ]                                 |
                  |                                      |
                  | Atomic Claim                         |
                  | (claimDueWeeklyOrgs)                 |
                  v                                      |
            [ PROCESSING ]                               |
                  |                                      |
         +--------+--------+                             |
         |                 |                             |
    Emit Success      Emit Failure                       |
         |                 |                             |
         v                 v                             |
   (advance lastRun,  (preserve lastRun,                 |
    advance nextRun,   reschedule nextRun to tomorrow,   |
    status=PENDING)    status=PENDING)                   |
         |                 |                             |
         +--------+--------+                             |
                  |                                      |
                  +--------------------------------------+
```

1. **Provisioning**: When an organization config is created (via `createMissingDefaultWeeklyConfig` or user preferences), it begins with `status = 'PENDING'`, `claimed_at = NULL`, `last_run = NULL`, and `next_run` set to the initial scheduled slot.
2. **Claiming**: At execution time, a pod atomically transitions due orgs (`status = 'PENDING' AND next_run <= :now`) to `status = 'PROCESSING'` and sets `claimed_at = :now`.
3. **Successful Execution**:
   - `status` resets to `'PENDING'`
   - `claimed_at` is cleared to `NULL`
   - `last_run` advances to the scheduled slot time (`calculateCurrentSlot`)
   - `next_run` advances to the next cron interval (`calculateNextRun`)
4. **Failed Execution**:
   - `status` resets to `'PENDING'`
   - `claimed_at` is cleared to `NULL`
   - `last_run` remains **unchanged** (preserving the event window start boundary for retries)
   - `next_run` is set to tomorrow at the same hour (`executionTime.plusDays(1)`) to avoid immediate retry loops
5. **Zero Events (Quiet Period)**:
   - `status` resets to `'PENDING'`
   - `claimed_at` is cleared to `NULL`
   - `last_run` advances to the scheduled slot time
   - `next_run` advances to the next cron interval
6. **Crash Recovery (Stale Claims)**:
   - If a pod crashes mid-execution (OOM, SIGKILL), the row remains in `PROCESSING`.
   - `resetStaleClaims()` resets rows to `'PENDING'` with `claimed_at = NULL` once `claimed_at` exceeds the staleness threshold.

### Transaction Semantics and Emit-Before-Persist Re-delivery Risk

The aggregation workflow follows an **emit-before-persist** design:

1. Pod claims due orgs in an atomic transaction (`claimDueWeeklyOrgs`).
2. Pod queries events and builds `AggregationCommand` instances.
3. Pod emits messages to Kafka via `emitterIngress.send(Message.of(action))`.
4. Pod updates database records with new `last_run`, `next_run`, and resets status to `PENDING` (`updateOrgConfigs`).

**Critical Invariant: At-Least-Once Delivery**:
Because Kafka emission occurs *before* database state persistence, any failure occurring after message emission (e.g., database timeout, network partition, pod crash before `updateOrgConfigs` commits) leaves claimed rows in `PROCESSING`. When these rows are subsequently reset via `resetStaleClaims()` or retried, the aggregation commands will be re-generated and re-emitted.

- **Downstream Idempotency**: Downstream consumers (the notifications engine and template renderers) must handle duplicate `AggregationCommand` messages idempotently.
- **Do Not Invert the Order**: Persisting state before emitting to Kafka is not viable because a failure during emission would drop the digest permanently with no record to retry.

---

## 2. next_run Advancement Rules

### Universal Advancement Across All Claimed Organizations

A critical requirement of the scheduling state machine is that **`next_run` must advance for every claimed organization**, regardless of whether the organization had events during the time window.

- **The Quiet Org Problem**: In any given period, many organizations will have zero events. If `next_run` is only updated when events exist, orgs with no events remain in the database with `next_run <= now`. On every subsequent 15-minute aggregator run, those orgs will be claimed again, queried again, and left un-advanced, causing continuous redundant database load.
- **Weekly Schedule Starvation**: For weekly digests, failing to advance `next_run` for quiet orgs means they stay permanently due throughout the week.

### The `finalizeRun` Pattern

`AbstractEmailAggregationJob` orchestrates the lifecycle and delegates quiet-org handling to subclasses via `finalizeRun()`:

```java
List<String> allUpdatedOrgIds = new ArrayList<>(successfulOrgIds);
allUpdatedOrgIds.addAll(failedOrgIds);
finalizeRun(allUpdatedOrgIds, claimedOrgIds, executionTime);
```

In `WeeklyEmailAggregationJob`:

```java
@Override
protected void finalizeRun(List<String> allUpdatedOrgIds, List<String> claimedOrgIds, LocalDateTime executionTime) {
    List<String> noEventsOrgIds = claimedOrgIds.stream()
        .filter(id -> !allUpdatedOrgIds.contains(id))
        .collect(Collectors.toList());
    digestTriggerOrgConfigRepository.advanceNextRunForOrgsWithNoEvents(executionTime, noEventsOrgIds);
}
```

The repository method `advanceNextRunForOrgsWithNoEvents()` advances both `last_run` and `next_run`, and resets `status = 'PENDING'` and `claimed_at = NULL` for all organizations that had no events in the current slot.

---

## 3. last_run Null Handling in New Configurations

### Newly Provisioned Configurations

Configurations are provisioned in two ways:
1. Batch background insertion: `createMissingDefaultWeeklyConfig()` runs at job start to insert default configurations for organizations with weekly email subscriptions.
2. User preferences: Backend preference endpoints insert or update `digest_trigger_org_config` rows when users configure email digest preferences.

In both cases, newly created records have `last_run = NULL`.

### Null Safety and Query Lower Bounds

Code interacting with `DigestTriggerOrgConfig` must never assume `last_run` is non-null.

1. **SQL / HQL Queries**:
   Event aggregation queries must coalesce `last_run` with a default lookback interval (e.g., 7 days for weekly subscriptions):
   ```sql
   AND ev.created > COALESCE(dtc.lastRun, :oneWeekAgo) AND ev.created <= :now
   ```
   *Anti-pattern to avoid*: Using `AND ev.created > dtc.lastRun AND ev.created > :oneWeekAgo`. If `dtc.lastRun` is null, the SQL expression evaluates to unknown/false, silently dropping all events for newly configured organizations.
   *Anti-pattern to avoid*: Combining both bounds with `AND` when `last_run` exists causes data loss on retries. If a run scheduled for Monday runs on Tuesday (due to a failure retry), `now - 7 days` becomes last Tuesday, silently dropping events between last Monday (`last_run`) and last Tuesday. Using `COALESCE(dtc.lastRun, :oneWeekAgo)` ensures that once `last_run` is established, it acts as the authoritative start boundary.

2. **Command Window Construction**:
   When building `AggregationCommand`, check for `last_run == null`:
   ```java
   if (lastRun == null) {
       start = now.minusDays(7);
       end = now;
   } else {
       start = lastRun;
       end = digestCronUtils.calculateCurrentSlot(cronExpression, now);
   }
   ```
   For first runs, using `[now.minusDays(7), now]` avoids cron calculation quirks where `calculateNextRun(cron, now)` returns the *next* week's occurrence when `now` matches the cron schedule.

3. **Status Update Updates**:
   When updating `last_run` during `updateLastAndNextRunForWeekly()` or `advanceNextRunForOrgsWithNoEvents()`:
   ```java
   LocalDateTime newLastRun = config.getLastRun() == null
       ? currentExecutionTime
       : digestCronUtils.calculateCurrentSlot(config.getCronExpression(), currentExecutionTime);
   ```
   Directly dereferencing `config.getLastRun()` without null checks will throw `NullPointerException`.

---

## 4. Event Window Calculation and Schedule Changes

### Anchoring to Scheduled Slot Time

The `AggregationCommand` defines the event query window `[start, end]` delivered to the template engine.

- **`start`**: `lastRun` (or `now.minusDays(7)` if first run).
- **`end`**: The scheduled slot time for the execution, computed via `digestCronUtils.calculateCurrentSlot(cronExpression, now)`.

### Why `calculateCurrentSlot` Instead of `calculateNextRun(cron, lastRun)`

Deriving the end of the window from the previous `lastRun` using `cron.nextExecution(lastRun)` is a critical failure mode when digest schedules change:

1. **The Schedule Change Bug**:
   - Organization starts with Monday 00:30 schedule. Last successful run was Monday Nov 9 (`lastRun = Nov 9`).
   - Admin changes schedule to Wednesday 09:00 on Sunday Nov 15.
   - `next_run` is updated to Wednesday Nov 18.
   - On Nov 18, the job runs.
   - If `end` is derived from `calculateNextRun(newCron, lastRun)`:
     `calculateNextRun(Wed cron, Nov 9) = Nov 11`.
     November 11 is already in the past! The resulting digest window is `[Nov 9, Nov 11]`, and every subsequent weekly digest permanently lags a full week behind the actual slot.
   - In contrast, `calculateCurrentSlot(Wed cron, Nov 18)` correctly returns `Nov 18`.

2. **Extended Window on Schedule Change**:
   Using `calculateCurrentSlot` ensures that following a schedule change, the first digest covers from the old schedule's `lastRun` (Nov 9) to the new schedule's first execution slot (Nov 18) — an extended 9-day window. This guarantees **zero dropped events** across schedule migrations. Subsequent digests return to the standard 7-day cadence.

3. **Retry Consistency**:
   When a scheduled job fails and retries later (e.g., job scheduled for Monday runs on Tuesday due to a 24-hour retry):
   - `calculateCurrentSlot(Monday cron, Tuesday)` still evaluates to Monday's slot.
   - The event window `[lastRun, slotEnd]` correctly reflects the intended weekly window without drift.

---

## 5. Concurrency Model and Stale-Claim Detection

The aggregator is designed for multi-pod deployments where multiple pods run concurrently or overlapping scheduled executions occur.

### Atomic Claiming Pattern

Pod coordination uses database-level optimistic locking via PostgreSQL's atomic `UPDATE ... RETURNING`:

```sql
UPDATE digest_trigger_org_config
SET status = 'PROCESSING', claimed_at = :now
WHERE status = 'PENDING'
  AND next_run <= :now
  AND subscription_type = 'WEEKLY'
RETURNING org_id;
```

- **Row Locks**: PostgreSQL acquires row-level locks during the update.
- **Disjoint Sets**: Concurrent pods executing this query concurrently receive completely disjoint sets of `org_id` values. No two pods can claim the same organization in the same cycle.

### Pod-Scoped Query Invariant

All subsequent queries in the job must filter by the specific `claimedOrgIds` returned to that pod:

```sql
SELECT ... FROM DigestTriggerOrgConfig dtc
WHERE dtc.id.subscriptionType = :subscriptionType
  AND dtc.id.orgId IN :claimedOrgIds
```

**Critical Invariant**: A query must NEVER simply check `WHERE status = 'PROCESSING'`. If pod A and pod B are running concurrently, both have claimed rows in `PROCESSING`. Querying solely on `status = 'PROCESSING'` causes pod B to process pod A's organizations, resulting in duplicate digests, data races, and race conditions on `next_run` updates.

### Stale-Claim Detection and Recovery

If an aggregator pod crashes (e.g., Kubernetes OOMKilled, node eviction, SIGKILL) while processing claimed rows, those rows would remain stuck in `PROCESSING` indefinitely.

To recover, `resetStaleClaims()` runs at the start of each claiming cycle:

```sql
UPDATE DigestTriggerOrgConfig
SET status = 'PENDING', claimed_at = null
WHERE status = 'PROCESSING'
  AND id.subscriptionType = :type
  AND (claimed_at < :threshold OR (claimed_at IS NULL AND nextRun < :threshold))
```

The threshold is configurable (`notifications.weekly.stale-claim-threshold-hours`, default 6 hours) and must exceed the maximum expected maintenance window plus job execution duration.

### Invariant: Staleness Must Compare Against `claimed_at`, NOT `next_run`

A common defect in crash-recovery logic is checking `next_run < threshold` instead of `claimed_at < threshold`.

- **The Delayed-Job Race Condition**:
  Assume the threshold is 6 hours. If the aggregator job is delayed by 7 hours (e.g., during database maintenance, cluster migration, or severe backlog), all due orgs have `next_run` timestamps that are 7 hours in the past.
  - Pod A claims the orgs: `status = 'PROCESSING'`, `claimed_at = now`.
  - Pod B runs `resetStaleClaims()` minutes later.
  - If `resetStaleClaims()` checked `next_run < threshold`, it would observe `next_run` (7 hours ago) < `threshold` (6 hours ago) and **immediately reset Pod A's rows back to `PENDING`** while Pod A is actively generating and sending digests!
  - Pod B would then claim those same rows, causing duplicate processing.
- **The Correct Check**:
  By checking `claimed_at < threshold`, Pod B sees that Pod A claimed the row only minutes ago (`claimed_at` is fresh), so the row is not stale.
- **Legacy Fallback**:
  For backward compatibility with rows created before `claimed_at` was introduced, `(claimed_at IS NULL AND nextRun < :threshold)` serves as a fallback.

---

## 6. Database Parameter Limits and Query Batching

### PostgreSQL JDBC Bind Parameter Ceiling

The PostgreSQL wire protocol and JDBC driver enforce a hard ceiling of **65,535 bind parameters** per prepared statement.

If an SQL query contains an `IN (:orgIds)` clause where `:orgIds` has more than 65,535 items, PostgreSQL throws a query execution error (`org.postgresql.util.PSQLException: Tried to send an out-of-range integer as a 2-byte value`).

### Batching Requirement

Any repository method that passes collections of IDs in `IN` clauses must partition the collection into batches well below this ceiling.

In `DigestTriggerOrgConfigRepository`:
```java
private static final int IN_CLAUSE_MAX = 32000;
```

A batch size of 32,000 provides safety headroom for statements that also bind other parameters.

### Partitioning Pattern

Use a partitioning utility to execute the query across batches and accumulate the results:

```java
private static <T> List<List<T>> partition(List<T> list, int batchSize) {
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
        batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return batches;
}
```

Apply this batching in all repository bulk operations:
- Fetching pending aggregations (`getApplicationsWithPendingWeeklyAggregation`)
- Updating successful configurations (`updateLastAndNextRunForWeekly`)
- Advancing quiet configurations (`advanceNextRunForOrgsWithNoEvents`)
- Rescheduling failed configurations (`updateNextRunForFailedWeekly`)

---

## 7. Feature Flag Gating and Cluster Coordination

### Unleash Feature Toggles for Rollout Safety

In accordance with [Configuration Guidelines](configuration-guidelines.md), new aggregation pipelines and subscription types must be protected by feature toggles.

In `AggregatorConfig`:
```java
@PostConstruct
void registerToggles() {
    activeClusterToggle = toggleRegistry.register("notifications-aggregator-active-cluster", true);
    suspendWeeklyAggregation = toggleRegistry.register("notifications-aggregator-suspend-weekly-aggregation", true);
}
```

- **Emergency Suspension**: `isWeeklyAggregationSuspended()` checks `notifications-aggregator-suspend-weekly-aggregation`. If an operational issue occurs in production, operators can instantly disable weekly digest processing without restarting pods or redeploying code.
- **Fail-Safe Defaults**: Toggles must default to safe states if the Unleash server is unreachable. For example, `unleash.isEnabled(suspendWeeklyAggregation, false)` returns `false` (not suspended) when Unleash is disabled locally.

### Cluster Coordination for Active-Standby Deployments

The aggregator runs across multi-cluster environments (e.g., active and standby clusters). To prevent concurrent executions across clusters:

1. `AggregatorConfig.getActiveCluster()` retrieves the currently active cluster ID from the `notifications-aggregator-active-cluster` Unleash variant payload.
2. `AbstractEmailAggregationJob.shouldRunOnThisCluster()` evaluates:
   ```java
   protected boolean shouldRunOnThisCluster() {
       Optional<String> activeCluster = aggregatorConfig.getActiveCluster();
       Optional<String> myCluster = aggregatorConfig.getClusterId();
       if (activeCluster.isEmpty() || myCluster.isEmpty()) {
           return true; // Run if cluster coordination is not configured
       }
       return activeCluster.get().equalsIgnoreCase(myCluster.get());
   }
   ```
3. If the pod is running on a standby cluster, the job logs an informational message and skips execution cleanly:
   ```text
   Weekly aggregator run skipped on secondary cluster due to Unleash flag
   ```

---

## Summary Checklist for Aggregator Scheduling PRs

When implementing or reviewing changes to aggregator scheduling logic, verify each invariant:

- [ ] **State Machine**: Does every code path return claimed rows from `PROCESSING` to `PENDING`?
- [ ] **Advance All Claimed**: Does `next_run` advance for orgs with zero events (`finalizeRun` / `advanceNextRunForOrgsWithNoEvents`)?
- [ ] **Null `last_run`**: Are new configs handled safely without NPE using `COALESCE(dtc.lastRun, :fallback)` and fallback window `[now - 7d, now]`?
- [ ] **Event Window**: Is `end` computed via `calculateCurrentSlot(cron, now)` rather than `calculateNextRun(cron, lastRun)`?
- [ ] **Pod Isolation**: Do queries filter by `IN :claimedOrgIds` rather than querying all rows with `status = 'PROCESSING'`?
- [ ] **Stale Claims**: Does `resetStaleClaims` check `claimed_at < threshold`, never `next_run < threshold`?
- [ ] **Parameter Limits**: Are `IN` clauses partitioned using `IN_CLAUSE_MAX` (<= 32,000)?
- [ ] **Feature Flags**: Are new aggregation types or risky scheduling paths gated behind Unleash toggles with fail-safe defaults?
- [ ] **At-Least-Once Awareness**: Is downstream command processing tolerant of potential re-deliveries caused by emit-before-persist?
