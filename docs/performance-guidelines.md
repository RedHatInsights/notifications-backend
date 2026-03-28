# Performance Guidelines

Rules and conventions for writing performant code in `notifications-backend`, derived from existing patterns. All agents MUST follow these during implementation and review.

## 1. Caching (Quarkus Caffeine)

### Cache naming and TTL conventions

The codebase uses `@CacheResult` / `@CacheInvalidate` from `io.quarkus.cache` with Caffeine as the backing provider. TTLs are configured in `application.properties` using `quarkus.cache.caffeine.<name>.expire-after-write`.

| TTL tier | Use case | Examples |
|---|---|---|
| PT1M | Volatile data fetched per-request (resolved recipients) | `recipients-resolver-results` |
| PT5M | Lookup data that changes infrequently (templates, event types) | `event-types-from-baet`, `event-types-from-fqn`, `aggregation-target-email-subscription-endpoints` |
| PT10M | External user directory responses | `recipients-users-provider-get-users`, `recipients-users-provider-get-group-users`, `find-recipients` |
| PT60s | Status / health checks, maintenance mode | `maintenance` |
| PT120s | RBAC authorization cache | `rbac-cache` |
| PT60M | Quasi-static organizational data | `kessel-rbac-workspace-id` |
| PT15M | Reference data (bundles, applications) | `get-bundle-by-id`, `get-app-by-name` |
| P7D | Credentials that rarely rotate | `kessel-oauth2-client-credentials` |

**Rules:**
- Always define `expire-after-write` in `application.properties` for new caches; never rely on Caffeine defaults.
- Enable `metrics-enabled=true` for caches that serve high-traffic paths (see `rbac-cache`, `recipients-users-provider-get-users`).
- When cached data is a collection fetched from an external service, copy it before mutating: `new HashSet<>(fetchedUsers)` (see `RecipientsResolver.findRecipients`).
- Use `@CacheInvalidateAll` to provide explicit cache-clearing endpoints (see `OAuth2ClientCredentialsCache.clearCache()`).
- Cache names must be kebab-case strings matching the property key.

## 2. Kafka Configuration

### Producer conventions
- Use `snappy` compression for topics carrying large payloads (rendered emails): `mp.messaging.outgoing.<channel>.compression.type=snappy`.
- Set `mp.messaging.outgoing.<channel>.merge=true` when multiple in-app emitters write to the same topic (see `egress` channel).
- Use structured cloud-events mode for outgoing connector messages: `cloud-events-mode=structured`.
- Monitor payload sizes with `DistributionSummary` before sending (see `ConnectorSender.recordMetrics`).
- When a payload exceeds `mp.messaging.outgoing.tocamel.max.request.size` (configured as 10 MB in this application; Kafka default is 1 MB), store it in the database and send only a reference ID (see `ConnectorSender.send` with `PayloadDetails`).

### Consumer conventions
- All Kafka consumers use `@Blocking` annotation to avoid starving the Vert.x event loop.
- Connector-v2 consumers use `@Blocking("connector-thread-pool")` combined with `@RunOnVirtualThread` (see `MessageConsumer`).
- The `connector-thread-pool` concurrency is configured via `smallrye.messaging.worker.connector-thread-pool.max-concurrency` (default 20 in connector-drawer).
- Set `mp.messaging.incoming.<channel>.pausable=true` for channels that may need flow-control at runtime (see `ingress`, `exportrequests`).
- Use a dedicated `group.id` per connector module (e.g., `notifications-connector-email`, `notifications-connector-slack`).

### High-volume topic pattern
- High-traffic applications (e.g., `errata-notifications`) are routed to a dedicated topic (`platform.notifications.connector.email.high.volume`) controlled by Unleash toggle `kafka-outgoing-high-volume-topic`.
- The decision is made in `ConnectorSender` based on application name; new high-volume apps require updating `isEventFromHighVolumeApplication`.

## 3. Thread Pool and Async Processing

### EventConsumer thread pool (engine)
The engine's `EventConsumer` uses a custom `ThreadPoolExecutor` with a blocking `LinkedBlockingQueue` that converts `offer()` to `put()` to apply backpressure on the Kafka consumer thread. Configuration (via `EngineConfig`):

| Property | Default | Purpose |
|---|---|---|
| `notifications.event-consumer.core-thread-pool-size` | 10 | Core threads |
| `notifications.event-consumer.max-thread-pool-size` | 10 | Max threads |
| `notifications.event-consumer.keep-alive-time-seconds` | 60 | Idle thread reclaim |
| `notifications.event-consumer.queue-capacity` | 1 | Blocking queue size |

**Rules:**
- The queue capacity of 1 is intentional: it blocks the Kafka consumer when all threads are busy, preventing unbounded memory growth.
- Async event processing is gated by an Unleash toggle (`async-event-processing`). Always check `config.isAsyncEventProcessing()`.
- When processing Kafka messages on worker threads, annotate the method with `@ActivateRequestContext` to ensure CDI request scope is available (see `EventConsumer.process`).

### Aggregation executor (engine)
Email aggregation uses a `ManagedExecutor` produced by `AggregationManagedExecutorProducer`:
- `notifications.aggregation.managed-executor.max-queued=100`
- `notifications.aggregation.managed-executor.max-async=10`
- Aggregation tasks use `@Dependent` scoped `AsyncAggregation` runnables with `@ActivateRequestContext`.

### Connector SEDA pattern
Connectors (email, slack, teams, etc.) uniformly configure:
- `notifications.connector.seda.concurrent-consumers=20`
- `notifications.connector.seda.queue-size=20`

Do NOT change these without load testing. They match the Camel HTTP component's `connectionsPerRoute` default.

## 4. REST Client Timeouts

Explicit timeouts are set per external service. Follow these conventions:

| Service | connect-timeout | read-timeout |
|---|---|---|
| RBAC (backend auth) | 2000ms | 2000ms |
| RBAC (recipients-resolver s2s) | 2000ms | 120000ms |
| Sources | - | 1000ms |
| Internal engine | - | 900000ms (15min) |

**Rules:**
- Always set explicit `connect-timeout` and `read-timeout` for new REST clients. Omitting them risks unbounded waits.
- The recipients-resolver has a long read timeout (120s) because RBAC user pagination for large orgs can be slow. Log slow requests exceeding `recipientsResolverConfig.getLogTooLongRequestLimit()`.

## 5. Retry and Resilience

### Failsafe retry pattern
External service calls use `dev.failsafe.RetryPolicy` with exponential backoff. This pattern is used consistently in:
- `FetchUsersFromExternalServices` (recipients-resolver)
- `ExternalRecipientsResolver` (connector-email, connector-drawer)
- `BOPManager` (connector-email)

**Rules:**
- Configure `withBackoff(initialRetryBackoff, maxRetryBackoff)` and `withMaxAttempts(maxRetryAttempts)`.
- Handle only `IOException.class` (network failures), not all exceptions.
- Increment failure counters on each retry (`onRetry`) and on final failure.
- The RBAC auth path uses Mutiny retry: `.onFailure(IOException | ConnectTimeoutException).retry().withBackOff().atMost()`.

### Endpoint error tracking
The engine tracks server errors per endpoint with `EndpointRepository.incrementEndpointServerErrors`:
- `processor.connectors.max-server-errors=10` (threshold before disabling)
- `processor.connectors.min-delay-since-first-server-error=2D` (grace period)
- Uses `PESSIMISTIC_WRITE` lock to prevent concurrent threads from sending duplicate disable notifications.

## 6. Database Query Patterns

### Eager fetching
- Use `JOIN FETCH` in JPQL to avoid N+1 queries when loading entity graphs (see `EventTypeRepository.getEventType` which fetches application and bundle).
- Use the `loadProperties` batch-loading pattern: group endpoints by type, then load properties in a single `WHERE id IN (:ids)` query per type (see `EndpointRepository.loadProperties`).

### Pessimistic locking
- Use `PESSIMISTIC_WRITE` (`SELECT FOR UPDATE`) only when atomic read-modify-write is required across pods (see `EndpointRepository.lockEndpoint` for server error counting).
- `BehaviorGroupRepository` also uses pessimistic locking for ordering operations.

### Update queries
- Prefer HQL `UPDATE` statements over loading + modifying + persisting when only a few columns change (see `incrementEndpointServerErrors`, `resetEndpointServerErrors`, `disableEndpoint`).

## 7. Observability for Performance

### Required metrics for new features
- **Counters:** Track rejected, processed, error, and duplicate events (see `EventConsumer` counter names).
- **Timers:** Wrap processing paths with `Timer.Sample` / `Timer.start()` (see `CONSUMED_TIMER_NAME`, `user-provider.get-users.total`).
- **Distribution summaries:** Measure payload sizes (`notifications.tocamel.payload.content.size`).
- **Gauges:** Track cardinality of cached data (see `user-provider.users` gauge in `FetchUsersFromExternalServices`).

### Slow request logging
- Log warnings when external service calls exceed a configurable threshold (see `FetchUsersFromExternalServices` comparing duration against `getLogTooLongRequestLimit()`).

## 8. Feature Toggle Guards

Performance-sensitive features are gated behind Unleash toggles. Always:
- Register toggles in the relevant `*Config` class's `@PostConstruct` via `toggleRegistry.register()`.
- Provide a non-Unleash fallback for local development (see `EngineConfig.isAsyncEventProcessing()`).
- Log toggle states at startup (see `EngineConfig.logConfigAtStartup`).
- Use org-scoped toggles (`UnleashContextBuilder.buildUnleashContextWithOrgId`) for gradual rollouts.

## 9. Event Deduplication

- Kafka-level deduplication via `MESSAGE_ID_HEADER` (extracted from the Kafka message or the event payload).
- Application-level deduplication via `EventDeduplicator.isNew(event)` using custom per-tenant logic.
- Always check deduplication BEFORE persisting the event to avoid wasted database writes.

## 10. Connector Concurrency

- All connectors share the same `tocamel` / `fromcamel` Kafka topic pair, disambiguated by the `x-rh-notifications-connector` header.
- Connector-v2 modules filter messages by checking the header against `connectorConfig.getSupportedConnectorHeaders()` and immediately ack unrelated messages.
- The email connector disables reinjections (`notifications.connector.kafka.maximum-reinjections=0`) because email delivery should not retry at the Kafka level.
