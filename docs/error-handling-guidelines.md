# Error Handling Guidelines

## REST API Exception Mappers (backend module)

- Register all JAX-RS exception mappers as `@Provider` beans in `com.redhat.cloud.notifications.routers` (or its subpackages).
- `JaxRsExceptionMapper` handles `WebApplicationException` and is the catch-all for the backend module. Check for `JsonParseException` wrapped as a cause and map it to 400.
- `ConstraintViolationExceptionMapper` returns a structured JSON body with `title`, `description`, and a `violations` array containing `field`/`message` pairs. Always return `application/json` with HTTP 400.
- `NotFoundExceptionMapper` returns `text/plain` with the exception message.
- For the `recipients-resolver` module, `WebApplicationExceptionMapper` mirrors the same logic as the backend's `JaxRsExceptionMapper`. Keep these two mappers in sync when modifying either.
- Throw standard JAX-RS exceptions (`BadRequestException`, `NotFoundException`, `ForbiddenException`) directly from repositories and routers. Include a descriptive message string for user-facing errors.

## REST Client Exception Mappers

- `BadRequestExceptionMapper` in the `templates` package implements `ResponseExceptionMapper` (MicroProfile REST Client), not JAX-RS `ExceptionMapper`. Use this pattern to forward error messages from downstream REST clients.

## Connector Exception Handling (Camel-based, v1)

- The error flow is defined in `EngineToConnectorRouteBuilder.configure()` via two `onException(Throwable.class)` blocks: one for retryable exceptions (matched by `RedeliveryPredicate`), one for non-retryable exceptions. Both route to `ExceptionProcessor`.
- Extend `ExceptionProcessor` in each connector module to customize failure behavior. Override `process(Throwable, Exchange)` and call `logDefault(t, exchange)` for unrecognized exceptions.
- Use `@Alternative` + `@Priority(0)` on connector-specific subclasses (e.g., `EmailExceptionProcessor`) to override the `@DefaultBean` base class via CDI.
- `ExceptionProcessor.process(Exchange)` handles Kafka reinjection: if `KAFKA_REINJECTION_COUNT < kafkaMaximumReinjections` (default 3), the message is reinjected to the incoming Kafka topic with exponential backoff (10s, 30s, 1min). After exhausting reinjections, the failure is sent to the engine via the `CONNECTOR_TO_ENGINE` route.
- Set `SUCCESSFUL` to `false` and `OUTCOME` to the error message on the Exchange before routing to the engine.

## Connector Exception Handling (v2, SmallRye Reactive Messaging)

- Extend `ExceptionHandler` (in `connector-common-v2`) to customize failure behavior. Override `process(Throwable, IncomingCloudEventMetadata)` and return a `HandledExceptionDetails` subclass.
- For HTTP connectors, extend `HttpExceptionHandler` which classifies errors into `HttpErrorType` values and populates `HandledHttpExceptionDetails` with `httpStatusCode`, `httpErrorType`, and `targetUrl`.
- Use `@Alternative` + `@Priority(0)` on connector-specific subclasses (e.g., `PagerDutyExceptionHandler`, `SlackExceptionHandler`, `DrawerExceptionHandler`).
- `MessageConsumer` catches all exceptions from `MessageHandler.handle()`, delegates to `ExceptionHandler.processException()`, and always sends a failure response to the engine via `OutgoingMessageSender.sendFailure()`. Messages are always acknowledged.

## Redelivery and Retry

- `RedeliveryPredicate` (v1, `connector-common`) defaults to retrying only on `IOException`. Extend it to add connector-specific retry conditions.
- `HttpRedeliveryPredicate` retries on HTTP 5xx, HTTP 429 (Too Many Requests), and `IOException`. All other HTTP errors are not retried.
- Redelivery config: `notifications.connector.redelivery.max-attempts` (default 2), `notifications.connector.redelivery.delay` (default 1000ms).
- Kafka reinjection config: `notifications.connector.kafka.maximum-reinjections` (default 3). Delay schedule: 10s, 30s, 1min, then half of `max-poll-interval-ms`.
- For RBAC calls, retry `IOException` and `ConnectTimeoutException` using Mutiny's `.onFailure().retry().withBackOff()` pattern, configured via `rbac.retry.*` properties.
- For Kessel gRPC calls, use MicroProfile `@Retry(retryOn = KesselTransientException.class)`. Transient codes: `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED`. On `UNAUTHENTICATED`, recreate the gRPC channel before retrying.

## HttpErrorType Classification

- Three separate `HttpErrorType` enums exist: `connector-common-http`, `connector-common-http-v2`, and `engine`. All must stay in sync with the same values: `SOCKET_TIMEOUT`, `CONNECT_TIMEOUT`, `CONNECTION_REFUSED`, `HTTP_3XX`, `HTTP_4XX`, `HTTP_5XX`, `SSL_HANDSHAKE`, `UNKNOWN_HOST`, `UNSUPPORTED_SSL_MESSAGE`.
- HTTP 429 is classified as `HTTP_5XX` (grouped with server errors for retry purposes).
- Log HTTP 4xx errors at client error log level; log HTTP 5xx and 429 at server error log level. Both levels are configurable via `HttpConnectorConfig`.

## Endpoint Disabling on Persistent Errors

- `EndpointErrorFromConnectorHelper` in the engine processes connector outcomes from the outgoing Kafka topic.
- HTTP 3xx and 4xx errors disable the endpoint immediately (considered a configuration problem).
- Server errors (`SOCKET_TIMEOUT`, `CONNECT_TIMEOUT`, `CONNECTION_REFUSED`, `HTTP_5XX`, `SSL_HANDSHAKE`, `UNKNOWN_HOST`) increment a counter; the endpoint is disabled after exceeding `processor.connectors.max-server-errors` (default 10).
- A successful delivery resets the server error counter to zero.
- When an endpoint is disabled, `IntegrationDisabledNotifier` sends a notification to the org.

## DelayedThrower Pattern

- Use `DelayedThrower.throwEventually(message, accumulator -> { ... })` when processing a loop where individual failures must not abort the entire loop.
- Catch exceptions inside the loop and add them to the accumulator. All accumulated exceptions are rethrown as suppressed exceptions in a single `DelayedException` after the loop completes.
- `EndpointProcessor.process()` uses this pattern to process all endpoint types even if some fail.

## Custom Domain Exceptions

- `ActionParsingException` — thrown when incoming Kafka action payloads cannot be deserialized. Extends `RuntimeException`.
- `TemplateNotFoundException` — thrown when no Qute template matches the integration type/bundle/application/eventType combination.
- `FilterExtractionException` and `TransformationException` — checked exceptions for the export service pipeline.
- `KesselTransientException` — marker exception wrapping `StatusRuntimeException` for gRPC retry logic.

## Verification

```shell
# Compile and run all tests (includes exception mapper tests)
./mvnw clean verify -pl backend -am

# Run connector-common tests (includes ConnectorRoutesTest, redelivery tests)
./mvnw clean verify -pl connector-common -am

# Run HTTP connector tests (includes HttpRedeliveryPredicateTest)
./mvnw clean verify -pl connector-common-http -am

# Run v2 HTTP connector integration tests
./mvnw clean verify -pl connector-common-http-v2 -am

# Run checkstyle validation across all modules
./mvnw checkstyle:check
```
