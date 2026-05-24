# Performance Guidelines

## Kafka Consumer Threading Model

### Engine: `@Blocking` on All Consumers

Annotate every `@Incoming` consumer method in the engine with `@Blocking` (from `io.smallrye.reactive.messaging.annotations.Blocking`). This offloads processing from the Vert.x event loop to a worker thread. All four engine consumers (`EventConsumer`, `ConnectorReceiver`, `ReplayEventConsumer`, `ExportEventListener`) follow this pattern.

Note: `ExportEventListener` imports `io.smallrye.common.annotation.Blocking` instead. Both imports are acceptable; use whichever matches the surrounding code.

### Connector-v2: Named Thread Pool with Virtual Threads

In `connector-common-v2`, the `MessageConsumer` uses `@Blocking("connector-thread-pool")` combined with `@RunOnVirtualThread`. Each connector module sets the concurrency via `smallrye.messaging.worker.connector-thread-pool.max-concurrency=20` in its `application.properties`. Preserve both annotations when modifying connector-v2 message handlers.

### Engine Async Event Processing Thread Pool

`EventConsumer` creates a custom `ThreadPoolExecutor` with a `LinkedBlockingQueue` whose `offer()` delegates to `put()`, making the submit call block when all threads are busy. This is intentional backpressure -- do not replace with a standard queue. Pool sizing is configured via:
- `notifications.event-consumer.core-thread-pool-size` (default: 10)
- `notifications.event-consumer.max-thread-pool-size` (default: 10)
- `notifications.event-consumer.queue-capacity` (default: 1)

The small queue capacity is deliberate. The blocking `offer()` prevents unbounded Kafka message consumption when threads are saturated.

## Kafka Channel Backpressure

### Pausable Channels

Mark incoming Kafka channels that support runtime pause/resume with `pausable=true` in `application.properties`. Currently enabled on `ingress` and `exportrequests` channels.

`KafkaChannelManager` reads pause/resume directives from an Unleash feature toggle (`notifications.kafka-channels`) and applies them per-host via `PausableChannel`. When adding new incoming channels that should support operational pause, add `mp.messaging.incoming.<channel>.pausable=true`.

### SEDA Backpressure (connector-common, Camel-based)

`SedaComponentConfigurator` sets `defaultBlockWhenFull=true` on the SEDA component. This blocks Kafka message consumption when the SEDA queue (sized by `notifications.connector.seda.queue-size`, default 1000) is full. Do not disable this -- it prevents OOM under load.

## High-Volume Traffic Routing

`ConnectorSender` routes events from high-volume applications (currently `errata-notifications`) to a dedicated Kafka topic (`platform.notifications.connector.email.high.volume`) when the Unleash toggle is enabled. Only `EMAIL_SUBSCRIPTION` connectors are compatible with the high-volume topic. When adding new high-volume applications, update `isEventFromHighVolumeApplication()` in `ConnectorSender`.

## Kafka Producer Configuration

Use `snappy` compression for outgoing Kafka topics that carry large payloads (currently `tocamel` and `highvolume`). When a payload exceeds `mp.messaging.outgoing.tocamel.max.request.size` (default 10 MB), `ConnectorSender` stores it in the `payload_details` database table and sends only the reference ID over Kafka. Preserve this offload pattern for any new large-payload channels.

## Caching

### Engine Caches

Use `@CacheResult` (Quarkus Caffeine cache) for frequently queried, rarely changing reference data. Current engine caches and their TTLs:
- `event-types-from-baet`, `event-types-from-fqn`: 5 min
- `get-bundle-by-id`: 15 min
- `get-app-by-name`: 15 min
- `aggregation-target-email-subscription-endpoints`: 5 min
- `recipients-resolver-results`: 1 min

When adding new caches, define `quarkus.cache.caffeine.<name>.expire-after-write` in `application.properties`. Prefer short TTLs (1-5 min) for data that changes during operations.

### Backend Caches

RBAC authentication results are cached for 120 seconds (`rbac-cache`). Kessel OAuth2 tokens are cached for 7 days. Do not cache per-request or user-specific data beyond authentication.

## Mutiny Reactive Patterns

Mutiny `Uni` is used exclusively in the `backend` module for authentication flows (`ConsoleIdentityProvider`, `RbacServer`). The engine and connectors do not use Mutiny reactive types for business logic -- they use imperative `@Blocking` consumers.

### Retry with Backoff on Uni

When calling external services that return `Uni`, apply retry with exponential backoff for transient failures. Follow the `ConsoleIdentityProvider` pattern:
```java
.onFailure(failure -> failure.getClass() == IOException.class)
.retry()
.withBackOff(initialBackOff, maxBackOff)
.atMost(maxRetryAttempts)
```
Filter retries to specific exception types -- never retry on all failures.

## Camel Connector Retry

Camel-based connectors (connector-common) use `maximumRedeliveries` with `RedeliveryPredicate` to control which exceptions trigger retries. Defaults: 2 max attempts, 1000 ms delay. When adding new connector exception handling, extend `RedeliveryPredicate` to match only retryable exceptions.

Kafka message reinjection (re-sending failed messages) uses `asyncDelayed()` on the Camel delay to avoid blocking threads during the wait period. Maximum reinjections default to 3 (`notifications.connector.kafka.maximum-reinjections`).

## Deduplication

Event deduplication uses Postgres (`ON CONFLICT DO NOTHING`) as the primary mechanism, with Valkey (Redis-compatible) as an experimental parallel path gated by `isValkeyEventDeduplicatorEnabled()`. When the Valkey result disagrees with Postgres, the Postgres result takes precedence. On processing failure, the Valkey deduplication key is rolled back via `removeEventFromDeduplication()`.

## Error Isolation

Use `DelayedThrower.throwEventually()` when processing multiple endpoints for a single event. This ensures one endpoint's failure does not prevent delivery to other endpoints. All exceptions are collected and rethrown as suppressed exceptions after the loop completes.

## `@ActivateRequestContext`

Annotate methods that run on non-CDI-managed threads (e.g., `ThreadPoolExecutor` submissions, `Runnable` implementations) with `@ActivateRequestContext`. Without it, CDI request-scoped beans and JPA `EntityManager` are unavailable. See `EventConsumer.process()` and `AsyncAggregation.run()`.

## Verification

```bash
# Check that all @Incoming consumers in engine use @Blocking
grep -rn "@Incoming" engine/src/main/java/ --include="*.java" -l | \
  xargs grep -L "@Blocking"

# Verify pausable channels are declared in application.properties
grep "pausable=true" engine/src/main/resources/application.properties

# Verify connector-v2 thread pool concurrency is set
grep "connector-thread-pool.max-concurrency" connector-*/src/main/resources/application.properties

# Verify all engine Caffeine caches have explicit TTLs
grep "quarkus.cache.caffeine" engine/src/main/resources/application.properties

# Confirm snappy compression on outgoing Kafka topics
grep "compression.type=snappy" engine/src/main/resources/application.properties
```
