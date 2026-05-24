# Integration Guidelines

## Connector Architecture (v2)

- Build new connectors on the v2 architecture (`connector-common-v2`). The v1 Camel-based architecture (`connector-common`, `EngineToConnectorRouteBuilder`) is legacy and only used by `connector-email`.
- Each connector is a standalone Quarkus application in its own Maven module named `connector-{name}/`.
- Depend on the appropriate shared modules via Maven:
  - All connectors: `notifications-connector-common-v2`
  - HTTP-based connectors: `notifications-connector-common-http-v2`
  - Connectors needing Sources secrets (webhook, pagerduty, servicenow, splunk): `notifications-connector-common-authentication-v2`

## Required Components for a New Connector

Implement exactly these `@ApplicationScoped` beans in the connector module:

1. **`{Name}MessageHandler extends MessageHandler`** -- Override `handle(IncomingCloudEventMetadata<JsonObject>)` to return `HandledMessageDetails` (or `HandledHttpMessageDetails` for HTTP connectors).
2. **`{Name}Notification extends NotificationToConnector`** -- Define the incoming payload fields with Jakarta Validation annotations (`@NotNull`, `@NotBlank`).
3. **`{Name}RestClient`** -- MicroProfile REST Client interface annotated with `@RegisterRestClient(configKey = "connector-rest-client")`.
4. **`{Name}ExceptionHandler extends ExceptionHandler`** (or `HttpExceptionHandler`) -- Override `process()` to classify connector-specific failures. Use `@Alternative @Priority(0)` to override the default bean.
5. **`{Name}ConnectorConfig extends ConnectorConfig`** (or `HttpConnectorConfig`) -- Register Unleash feature toggles in `@PostConstruct` via `toggleRegistry.register()`. Override `getLoggedConfiguration()` to include connector-specific settings.

## application.properties Convention

Every connector must set these three properties:

```properties
notifications.connector.name=slack
notifications.connector.supported-connector-headers=${notifications.connector.name}
mp.messaging.incoming.incomingmessages.group.id=notifications-connector-slack
```

- `supported-connector-headers` can list multiple values (e.g., `ansible,webhook` for the webhook connector) to handle multiple endpoint types on one connector.
- Set `quarkus.http.port` to a unique port per connector to avoid conflicts during local development.
- Set `smallrye.messaging.worker.connector-thread-pool.max-concurrency=20` for parallel message processing.

## REST Client Patterns

- Use `configKey = "connector-rest-client"` for all outbound HTTP REST clients that call external services (Slack, PagerDuty, ServiceNow, etc.).
- Use `@Url String url` parameter with `io.quarkus.rest.client.reactive.Url` for dynamic target URLs.
- Apply `@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2)` at the interface level for retry with 3 total attempts.
- Always close `Response` objects with try-with-resources: `try (Response response = client.post(...))`.

## Message Flow

1. Engine sends CloudEvents to `platform.notifications.tocamel` Kafka topic.
2. `MessageConsumer` reads from `incomingmessages` channel, filters by `x-rh-notifications-connector` header, and delegates to `MessageHandler.handle()`.
3. Handler processes the message and returns `HandledMessageDetails`.
4. `OutgoingMessageSender` sends success/failure results back to `platform.notifications.fromcamel` as CloudEvents with type `com.redhat.console.notifications.history`.

Do not interact with Kafka directly. Use the `MessageHandler.handle()` override as the sole entry point for connector logic.

## Notification Validation

- Deserialize CloudEvent data via `incomingCloudEvent.getData().mapTo(YourNotification.class)`.
- Validate using `jakarta.validation.Validator.validate()` and throw `ConstraintViolationException` on violations. Follow the existing pattern:
  ```java
  Set<ConstraintViolation<T>> violations = validator.validate(notification);
  if (!violations.isEmpty()) {
      String errorMessage = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.joining(", "));
      throw new ConstraintViolationException("Validation failed: " + errorMessage, violations);
  }
  ```
- For HTTP connectors using the standard payload format (`NotificationToConnectorHttp`), inject and use `HttpNotificationValidator.parseAndValidate()` instead of writing validation manually.

## Authentication via Sources API

- `AuthenticationLoader` fetches secrets from the Sources API. It supports two client types, toggled by the `sources-hcc-cluster` Unleash feature flag:
  - `SourcesOidcClient` (configKey `sources-oidc`): Uses `@OidcClientFilter` for bearer token auth.
  - `SourcesPskClient` (configKey `sources`): Uses a PSK header. PSK-based auth is being deprecated.
- Only `AuthenticationType.SECRET_TOKEN` and `AuthenticationType.BEARER` are supported. Connectors must check the type and throw `IllegalStateException` for unsupported types.
- Connectors that need Sources secrets (webhook, pagerduty, servicenow, splunk) must configure both `sources` and `sources-oidc` REST client URLs and OIDC client settings in `application.properties`.

## RBAC Integration

- **Backend module**: `RbacServer` (configKey `rbac-authentication`) uses `x-rh-identity` header for user-context RBAC calls. Always register `RbacClientResponseFilter` via `@RegisterProvider`.
- **Workspaces**: Two client interfaces exist for the v2 workspaces API:
  - `RbacWorkspacesOidcClient` (configKey `rbac-authentication-oidc`): Uses `@OidcClientFilter`.
  - `RbacWorkspacesPskClient` (configKey `rbac-authentication`): Uses `x-rh-rbac-psk` and `x-rh-rbac-client-id` headers.
- **Recipients-resolver module**: Uses `RbacServiceToService` base interface with two implementations:
  - `RbacServiceToServiceOidc` (configKey `rbac-s2s-oidc`): Uses `@OidcClientFilter`.
  - `RbacServiceToServicePsk` (configKey `rbac-s2s`): Uses `AuthRequestFilter` to inject PSK headers.
- Prefer OIDC-based clients for new integrations. PSK clients are being phased out.
- RBAC API paths require trailing slashes (e.g., `/api/rbac/v1/access/`).

## Template-Based Connectors

Slack, Microsoft Teams, and Google Chat use `TemplateService` for message rendering:

- Inject `TemplateService` and construct a `TemplateDefinition` with the appropriate `IntegrationType` enum value (`SLACK`, `MS_TEAMS`, `GOOGLE_CHAT`).
- Extract `bundle`, `application`, and `event_type` from `notification.eventData` for template selection.
- Use the connector config's `isUseBetaTemplatesEnabled()` to check the Unleash toggle before selecting beta templates.

## Feature Toggles

- Register toggles in the connector config's `@PostConstruct` via `toggleRegistry.register("toggle-name", defaultValue)`.
- Query toggles with `unleash.isEnabled(toggleName, unleashContext, fallback)` guarded by `if (unleashEnabled)`.
- Build Unleash context using `UnleashContextBuilder.buildUnleashContextWithOrgId(orgId)` for org-scoped toggles.

## Testing

- Extend `BaseConnectorIntegrationTest` (or `BaseHttpConnectorIntegrationTest` for HTTP connectors) for integration tests.
- Override `buildIncomingPayload(String targetUrl)` to provide connector-specific CloudEvent data.
- Override `getRemoteServerPath()` to set the WireMock stub path.
- Annotate tests with `@QuarkusTest` and `@QuarkusTestResource(TestLifecycleManager.class)`.
- Use `smallrye-in-memory` connectors in test `application.properties`:
  ```properties
  mp.messaging.incoming.incomingmessages.connector=smallrye-in-memory
  mp.messaging.outgoing.outgoingmessages.connector=smallrye-in-memory
  ```
- Use `MicrometerAssertionHelper` for metrics assertions (success/failure counters, handler duration timer).

## Verification

```shell
# Compile all connector modules
./mvnw compile -pl connector-common-v2,connector-common-http-v2,connector-common-authentication-v2,connector-slack,connector-webhook,connector-pagerduty,connector-servicenow,connector-splunk,connector-google-chat,connector-microsoft-teams,connector-drawer

# Run a single connector's tests
./mvnw verify -pl connector-slack

# Run all connector tests
./mvnw verify -pl connector-slack,connector-webhook,connector-pagerduty,connector-servicenow,connector-splunk,connector-google-chat,connector-microsoft-teams,connector-drawer
```
