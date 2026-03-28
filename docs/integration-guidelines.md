# Integration Guidelines

Rules and conventions for integrations in `notifications-backend`, derived from existing patterns. All agents MUST follow these during implementation and review.

## Architecture Overview

This repository has two connector framework generations running in parallel:

- **V1 (Camel-based)**: Uses Apache Camel routes with `EngineToConnectorRouteBuilder` and SEDA queues. Connectors: Slack, PagerDuty, ServiceNow, Splunk, Email, Drawer, Google Chat, Microsoft Teams.
- **V2 (SmallRye Reactive Messaging)**: Uses `@Incoming`/`@Outgoing` annotations with `MessageHandler` pattern. Connectors: Webhook, Drawer. New connectors should use V2.

## Kafka Messaging Patterns

### Topic Convention
- Engine-to-connector: `platform.notifications.tocamel` (channel name: `tocamel` in engine, `incomingmessages` in V2 connectors)
- Connector-to-engine: `platform.notifications.fromcamel` (channel name: `fromcamel` in engine, `outgoingmessages` in V2 connectors)
- High-volume: `platform.notifications.connector.email.high.volume` (channel: `highvolume`)

### Message Routing
Messages are routed to connectors via the Kafka header `x-rh-notifications-connector`. Each connector declares which header values it accepts via `notifications.connector.supported-connector-headers` (comma-separated list). A connector can accept multiple header values (e.g., webhook accepts `ansible,webhook`).

### CloudEvent Format
All messages use CloudEvents spec version `1.0` in structured mode.

**Outgoing from engine** (`ConnectorSender`):
- `type`: `com.redhat.console.notification.toCamel.<connector-name>`
- `id`: the notification history UUID
- `data`: JSON with `org_id`, `endpoint_id`, plus connector-specific payload
- Includes `TracingMetadata` for OpenTelemetry context propagation

**Return to engine** (`OutgoingCloudEventBuilder`):
- `type`: `com.redhat.console.notifications.history`
- `source`: connector name
- `id`: same history UUID from the incoming event
- `data`: JSON with `successful` (boolean), `duration` (long ms), `details` (object with `type`, `target`, `outcome`)

### V2 Kafka Channel Configuration
In `connector-common-v2/src/main/resources/application.properties`:
```properties
mp.messaging.incoming.incomingmessages.connector=smallrye-kafka
mp.messaging.incoming.incomingmessages.topic=platform.notifications.tocamel
mp.messaging.outgoing.outgoingmessages.connector=smallrye-kafka
mp.messaging.outgoing.outgoingmessages.topic=platform.notifications.fromcamel
mp.messaging.outgoing.outgoingmessages.cloud-events-mode=structured
```

## Connector Module Architecture

### V1 Connector (Camel-based) Structure

**Required classes:**
1. **RouteBuilder** -- Extend `EngineToConnectorRouteBuilder`, implement `configureRoutes()`. Must be `@ApplicationScoped`. Route starts from `seda(ENGINE_TO_CONNECTOR)` and ends at `direct(SUCCESS)`.
2. **CloudEventDataExtractor** -- Extend `CloudEventDataExtractor`, override `extract(Exchange, JsonObject)` to parse `cloudEventData` into exchange properties. For HTTP-based connectors, typically set `TARGET_URL`. Must be `@ApplicationScoped`.

**Optional overrides (all use `@DefaultBean` pattern):**
- `ExceptionProcessor` -- Override `process(Throwable, Exchange)` for custom error handling.
- `RedeliveryPredicate` -- Override `matches(Exchange)` to control which exceptions trigger redelivery (default: `IOException`).
- `OutgoingCloudEventBuilder` -- Override to customize the return CloudEvent.

**Route flow**: Kafka -> filter by connector header -> strip Kafka headers -> extract CloudEvent -> SEDA queue -> `configureRoutes()` -> `direct:success` -> build outgoing CloudEvent -> Kafka return.

### V2 Connector (Reactive Messaging) Structure

**Required class:**
1. **MessageHandler** -- Extend `MessageHandler`, override `handle(IncomingCloudEventMetadata<JsonObject>)`. Return a `HandledMessageDetails` subclass. Must be `@ApplicationScoped`.

**Optional overrides:**
- `ExceptionHandler` -- Override `process(Throwable, IncomingCloudEventMetadata)` for custom error handling. Return a `HandledExceptionDetails` subclass.
- `OutgoingCloudEventBuilder` -- Override `buildSuccess(HandledMessageDetails)` and `buildFailure(HandledExceptionDetails)` to add metadata to the outgoing CloudEvent.
- `ConnectorConfig` -- Extend to add connector-specific configuration.

**Message flow**: `@Incoming("incomingmessages")` -> `@Blocking("connector-thread-pool")` + `@RunOnVirtualThread` -> filter by connector header -> `MessageHandler.handle()` -> `OutgoingMessageSender.sendSuccess/sendFailure()` -> `@Channel("outgoingmessages")` via `Emitter<String>`.

**Threading model**: V2 `MessageConsumer.processMessage()` uses `@Blocking("connector-thread-pool")` combined with `@RunOnVirtualThread`. Configure concurrency per connector via `smallrye.messaging.worker.connector-thread-pool.max-concurrency` (e.g., 20 in connector-drawer).

### HTTP Connector Layers

For HTTP-based connectors, use the shared HTTP infrastructure modules:

- **V1**: Depend on `connector-common-http`. Provides `HttpConnectorConfig`, `HttpExceptionProcessor`, `HttpRedeliveryPredicate`, and `HttpOutgoingCloudEventBuilder`.
- **V2**: Depend on `connector-common-http-v2`. Provides `HttpConnectorConfig`, `HttpExceptionHandler`, `HttpOutgoingCloudEventBuilder`, `HttpNotificationValidator`, and `HttpRestClient` (in `connector-common-http-v2` module).

HTTP connectors classify errors by type: `SOCKET_TIMEOUT`, `CONNECT_TIMEOUT`, `CONNECTION_REFUSED`, `HTTP_3XX`, `HTTP_4XX`, `HTTP_5XX`, `SSL_HANDSHAKE`, `UNKNOWN_HOST`, `UNSUPPORTED_SSL_MESSAGE`. Status 429 (Too Many Requests) is grouped with 5XX for retry purposes.

**V2 HTTP validation**: Use `HttpNotificationValidator.parseAndValidate(incomingCloudEvent)` to deserialize and validate `NotificationToConnectorHttp` with Bean Validation (`jakarta.validation`). Throws `ConstraintViolationException` on failure.

## REST Client Patterns

### Registration Convention
Use `@RegisterRestClient(configKey = "<name>")` on interfaces. Configure in `application.properties`:
```properties
quarkus.rest-client.<name>.url=${clowder.endpoints.<service>.url:http://localhost:8000}
quarkus.rest-client.<name>.trust-store=${clowder.endpoints.<service>.trust-store-path}
quarkus.rest-client.<name>.trust-store-password=${clowder.endpoints.<service>.trust-store-password}
quarkus.rest-client.<name>.trust-store-type=${clowder.endpoints.<service>.trust-store-type}
```

### Retry with MicroProfile Fault Tolerance
Apply `@Retry` at the interface level, not per method:
```java
@RegisterRestClient(configKey = "connector-rest-client")
@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2)
public interface WebhookRestClient { ... }
```

### Dynamic URLs
Use `@Url` from `io.quarkus.rest.client.reactive` for dynamic target URLs on REST client methods. V1 connectors use Camel's `toD()` with `${exchangeProperty.targetUrl}` instead.

## Retry and Redelivery

### Two-level retry strategy:
1. **Camel redelivery (V1 only)**: Configured via `ConnectorConfig`. Default: 2 max attempts, 1000ms delay. Controlled by `RedeliveryPredicate` (default: retries on `IOException`).
2. **REST client retry (V2)**: Use `@Retry` annotation. Convention: `delay=1s, maxRetries=2` (3 total attempts).
3. **Kafka reinjection (V1 only)**: After redelivery exhaustion, messages are reinjected to the incoming Kafka topic with exponential backoff (10s, 30s, 60s). Maximum reinjections: 3 (configurable via `notifications.connector.kafka.maximum-reinjections`). After exhaustion, failure is reported to the engine.

### V2 error handling
V2 connectors do not use Kafka reinjection. Exceptions thrown from `MessageHandler.handle()` are caught by `MessageConsumer`, processed through `ExceptionHandler`, and a failure response is sent back to the engine immediately. The message is always acknowledged (`message.ack()`) regardless of success or failure -- no Kafka-level retries.

## Authentication with Sources API

Connectors needing endpoint authentication use the `connector-common-authentication` (V1) or `connector-common-authentication-v2` module:

1. `AuthenticationLoader.fetchAuthenticationData(orgId, authenticationJson)` calls Sources API to retrieve secrets.
2. Sources API supports two auth mechanisms behind a feature toggle (`sources-oidc-auth`): OIDC client (via `SourcesOidcClient`) or PSK (via `SourcesPskClient`). PSK is deprecated.
3. Authentication types: `BEARER` (Authorization header) and `SECRET_TOKEN` (X-Insight-Token header).
4. The `AuthenticationRequest` requires both `authenticationType` and `secretId` fields.

## Configuration Conventions

### Connector Config Properties
All connector config uses the `notifications.connector.*` namespace:
- `notifications.connector.name` -- Connector identifier (required)
- `notifications.connector.supported-connector-headers` -- Comma-separated accepted routing headers (required)
- `notifications.connector.kafka.incoming.topic` / `outgoing.topic` -- Kafka topics
- `notifications.connector.kafka.incoming.group-id` -- Consumer group
- `notifications.connector.redelivery.max-attempts` -- Default: 2
- `notifications.connector.redelivery.delay` -- Default: 1000ms
- `notifications.connector.seda.concurrent-consumers` -- Default: 1, typically overridden to 20 (V1 only)
- `notifications.connector.seda.queue-size` -- Default: 1000, typically overridden to 20 (V1 only)

### Config Class Hierarchy
Extend `ConnectorConfig` (or `HttpConnectorConfig` for HTTP connectors) using `@Priority` for precedence. Base priority is `0`; HTTP config uses `BASE_CONFIG_PRIORITY + 1`. Override `getLoggedConfiguration()` to include custom config in startup logs.

## Testing Patterns

### V1 Integration Tests
Extend `ConnectorRoutesTest` from `connector-common`. Implement:
- `buildIncomingPayload(String targetUrl)` -- Build the CloudEvent data payload
- `checkOutgoingPayload(JsonObject)` -- Return a Camel `Predicate` to verify the outgoing payload
- `getMockEndpointPattern()` / `getMockEndpointUri()` -- WireMock endpoint patterns

Tests use `CamelQuarkusTestSupport` with `AdviceWith` to mock Kafka endpoints and replace with `direct:` routes.

### V2 Integration Tests
Extend `BaseConnectorIntegrationTest` (or `BaseHttpConnectorIntegrationTest` for HTTP connectors) from `connector-common-v2`. Implement:
- `buildIncomingPayload(String targetUrl)` -- Build the CloudEvent data payload
- `getConnectorSpecificTargetUrl()` -- Return the target URL (HTTP base provides this automatically)
- `getRemoteServerPath()` -- Return the mock server path
- `afterSuccessfulNotification(List<LoggedRequest>)` -- Optional hook for post-success assertions

Tests use `InMemoryConnector` from SmallRye to replace Kafka with in-memory channels. Use `Awaitility` for async assertions. WireMock is used for external HTTP service mocking. Send messages via `sendCloudEventMessage()`, assert with `assertSuccessfulOutgoingMessage()` / `assertFailedOutgoingMessage()`.

### Test Lifecycle
Both V1 and V2 use `TestLifecycleManager` to manage WireMock server lifecycle via `@QuarkusTestResource`. Use `@InjectMock` / `@InjectSpy` for mocking injected dependencies (e.g., `@InjectMock @RestClient SourcesPskClient`).

### Metrics Assertions
Use `MicrometerAssertionHelper` for counter-based assertions. Standard metrics: `connector.messages.processed`, `connector.messages.succeeded`, `connector.messages.failed` (all tagged by `connector` name). Assert with `assertMetricsIncrement(processed, succeeded, failed)`.

## External Service Integration

### RBAC (Backend only)
`RbacServer` interface with `@RegisterRestClient(configKey = "rbac-authentication")`. Passes `x-rh-identity` header. Uses `@RegisterProvider(RbacClientResponseFilter.class)` for response filtering.

### Kessel (Backend only)
Authorization via `KesselCheckClient`. Uses `KesselPermission` and `WorkspacePermission` enums. Applied via `@Authorization` interceptor annotation.

### Sources API (Connectors)
Used by connectors requiring endpoint authentication (Webhook, PagerDuty, ServiceNow, Splunk). Two client variants: PSK (deprecated) and OIDC. Config key: `sources` / `sources-oidc`.

### Feature Toggles
Uses Unleash for feature flags. Register toggles via `ToggleRegistry.register(name, defaultValue)` in `@PostConstruct`. Check with `unleash.isEnabled(toggle, context, defaultValue)`. Build context with `UnleashContextBuilder.buildUnleashContextWithOrgId(orgId)`.

### Health Checks
V2 connectors include a `LivenessService` (`@Liveness`) that integrates with Unleash-based pod restart requests via `PodRestartRequestedChecker`. This is provided by `connector-common-v2` and requires no custom implementation.

### Large Payload Handling (Engine)
When an email connector payload exceeds `engineConfig.getKafkaToCamelMaximumRequestSize()`, the engine stores the payload in the database via `PayloadDetailsRepository` and sends only a `payload_details_id` reference over Kafka. The connector fetches the full payload from the database.

### Model Classes
- Use `@RegisterForReflection` on model classes deserialized from JSON (required for native compilation).
- Use Jackson `@JsonProperty` for JSON field mapping (e.g., `@JsonProperty("org_id")`).
- V2 HTTP models use Bean Validation annotations for input validation.
