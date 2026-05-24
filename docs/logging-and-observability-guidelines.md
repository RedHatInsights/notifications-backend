# Logging and Observability Guidelines

## Logging Framework

- Use `io.quarkus.logging.Log` (static methods) for all application logging. Do not instantiate loggers or use `@Slf4j`.
- The only exception to the above: `RbacClientResponseFilter` and `LogLevelManager` use `java.util.logging.Logger` because they interact with JUL-level APIs directly. Do not introduce new JUL usage elsewhere.
- Prefer the format-style methods (`Log.infof`, `Log.debugf`, `Log.warnf`, `Log.errorf`) with `%s`/`%d` placeholders over concatenation or parameterized `{}` style.
- Use `Log.logf(level, ...)` when the log level is configurable at runtime (see `HttpExceptionProcessor` pattern for HTTP error logging with configurable client/server error levels).

## Log Levels

- **INFO**: Request lifecycle events (sending notifications, subscription changes, startup configuration). Use `Log.infof` with structured context.
- **DEBUG**: Internal routing decisions, cache hits, resolved recipients, aggregation details. Guard expensive debug computation with `Log.isDebugEnabled()`.
- **WARN**: Recoverable issues (missing payloads, fallback behavior, skipped aggregations). Include `@channel` prefix when the warning warrants team attention in chat (e.g., `Log.warn("@channel Skipping event processing because...")`).
- **ERROR**: Unrecoverable failures (Kafka payload parsing, heap dump generation, notification history creation). Always pass the exception as the first argument: `Log.errorf(e, "message %s", arg)`.
- **TRACE**: Only for high-frequency request path logging (URI inspection). Always guard with `Log.isTraceEnabled()`.

## Log Message Format

- Include structured context in bracket notation: `[orgId=%s, eventId=%s, connector=%s, historyId=%s]` or `[org_id: %s][workspace_id: %s]`.
- Sanitize user-controlled input before logging to prevent log injection. Use the `ANTI_INJECTION_PATTERN` approach from `IncomingRequestInterceptor`:
```java
private static final Pattern ANTI_INJECTION_PATTERN = Pattern.compile("[\n|\r|\t]");
String sanitized = ANTI_INJECTION_PATTERN.matcher(input).replaceAll("");
```

## MDC and Access Logs

- Set `x-rh-org-id` in MDC via `org.jboss.logging.MDC.put("x-rh-org-id", orgId)` in request interceptors. This value appears in the access log pattern.
- The backend access log pattern includes `orgId=%{X,x-rh-org-id} traceId=%{X,traceId} spanId=%{X,spanId}`. Other modules use the `combined` pattern.
- Access logs filter out successful `/health` and `/metrics` requests via `ACCESS_LOG_FILTER_PATTERN` in `StartupUtils.initAccessLogFilter()`. Replicate this pattern in any new HTTP-serving module.

## Startup Logging

- Every module logs `=== Startup configuration ===` followed by key-value pairs of its configuration at startup.
- Call `startupUtils.logGitProperties()` and `startupUtils.logExternalServiceUrl(...)` from the module's `RunOn*Startup` class.
- In connector modules, call `connectorConfig.log()` which iterates `getLoggedConfiguration()`. Override `getLoggedConfiguration()` in subclasses to add connector-specific properties.

## Dynamic Log Level Management

- Log levels are managed at runtime via Unleash feature toggle `notifications.log-levels`. The `LogLevelManager` class in `common-unleash` handles this.
- Do not build custom log level management; use the existing Unleash-driven mechanism.
- HTTP connector error log levels are configurable via `notifications.connector.http.client-error.log-level` and `notifications.connector.http.server-error.log-level` (default: `DEBUG`).

## Metrics (Micrometer)

- Inject `MeterRegistry` via `@Inject MeterRegistry registry` (or `meterRegistry`). Do not create custom registries.
- Define metric names as `static final String` constants in the class that registers them. Expose these constants as `public` so tests can reference them.
- Prefer the `notifications.` prefix for metric names in backend/engine (e.g., `notifications.kessel.inventory.permission.check.requests`). Engine input processing uses unprefixed names (e.g., `input.rejected`, `input.consumed`). Connector metrics use `notifications.connector.` prefix.
- Use `Timer.Sample` pattern for measuring request durations:
```java
Timer.Sample sample = Timer.start(meterRegistry);
// ... operation ...
sample.stop(meterRegistry.timer("metric.name", Tags.of("key", "value")));
```
- Use `DistributionSummary.builder(...)` for payload size measurements (see `ConnectorSender.recordMetrics`).
- Use `Counter` via `meterRegistry.counter(name, tags).increment()` for event counting. Define tag keys as constants.

## Testing Metrics

- Use `MicrometerAssertionHelper` from `common/src/test/java` for metric assertions.
- Call `saveCounterValuesBeforeTest(...)` in `@BeforeEach`, then `assertCounterIncrement(...)` or `awaitAndAssertCounterIncrement(...)` after the operation.
- For async operations, use `awaitAndAssertCounterIncrement` which polls with Awaitility (30-second timeout).
- Call `clearSavedValues()` in `@AfterEach` to reset state between tests.
- Do not call `MeterRegistry.clear()` between tests; it removes counter instances held by `@ApplicationScoped` beans.

## Tracing

- Trace context propagation across Kafka uses `TracingMetadata.withPrevious(Context.current())` from SmallRye Reactive Messaging (see `ConnectorSender.buildMessage`).
- The backend access log includes `traceId` and `spanId` from MDC automatically via the configured pattern. No manual trace ID logging needed in HTTP-serving code.

## Health Checks

- Use MicroProfile Health annotations: `@Liveness` for liveness probes, `@Readiness` for readiness probes. Implement `HealthCheck` interface.
- Health endpoints: `/health/live` and `/health/ready` (engine), `/q/health/live` and `/q/health/ready` (backend, connectors, MCP).
- `LivenessService` in engine checks both `KafkaConsumedTotalChecker` (stalled consumption detection) and `PodRestartRequestedChecker` (Unleash-driven restart).
- `ValkeyHealthCheck` is both `@Readiness` and `@Liveness`.
- `HeapDumpResource` exists in backend, engine, and connector-common for on-demand heap dumps via `/api/internal/heap_dump/{hostname}`.

## External Log Sinks

- CloudWatch logging is configured per module via `quarkus.log.cloudwatch.*` properties (disabled by default, enabled in OpenShift).
- Sentry integration is configured via `quarkus.log.sentry.*` properties (disabled by default). Set `in-app-packages` to `com.redhat.cloud.notifications` for connectors, `*` for backend/aggregator.

## Verification

```shell
# Confirm no SLF4J or @Slf4j usage in production code
grep -rn "import org.slf4j\|@Slf4j" --include="*.java" */src/main/java/

# Confirm io.quarkus.logging.Log is used (not logger instances)
grep -rn "new Logger\|LoggerFactory.getLogger\|getLogger(getClass" --include="*.java" */src/main/java/ | grep -v LogLevelManager | grep -v RbacClientResponseFilter | grep -v StartupUtils

# Check metric names follow naming conventions
grep -rn "counter(\|timer(\|DistributionSummary" --include="*.java" */src/main/java/ | grep -v import

# Verify access log filter is initialized in startup classes
grep -rn "initAccessLogFilter" --include="*.java" */src/main/java/
```
