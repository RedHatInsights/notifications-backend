# Testing Guidelines

## Test Framework and Annotations

- Use `@QuarkusTest` for all tests; this repo does not use `@QuarkusIntegrationTest`.
- Always pair `@QuarkusTest` with `@QuarkusTestResource(TestLifecycleManager.class)` for modules that need database or mock server setup (backend, engine, aggregator, connectors).
- Each module has its own `TestLifecycleManager` in `com.redhat.cloud.notifications` that implements `QuarkusTestResourceLifecycleManager`. Reuse the module's existing one; do not create new lifecycle managers unless adding a new external dependency.
- For tests requiring specific OIDC or external service mocks, add additional `@QuarkusTestResource` annotations (e.g., `OidcServerMockResource`, `RbacServerMockResource`, `SourcesServerMockResource`).

## Database Test Isolation

- Extend `DbIsolatedTest` for any backend test that writes to the database. It injects `DbCleaner` and calls `clean()` in both `@BeforeEach` and `@AfterEach`, which truncates all entity tables and restores default bundle/app/event-type records.
- Do not call `DbCleaner.clean()` manually if already extending `DbIsolatedTest`.
- Use the module's `ResourceHelpers` (`@ApplicationScoped`, `@Transactional`) to create test fixtures (bundles, applications, event types, endpoints, behavior groups). Each module (backend, engine, aggregator) has its own `ResourceHelpers` tailored to its entity set.
- Use constants from `TestConstants` for default org IDs, account IDs, API paths, and container versions (`POSTGRES_MAJOR_VERSION`, `VALKEY_MAJOR_VERSION`).

## Database Containers

- `TestLifecycleManager` starts a Testcontainers `PostgreSQLContainer` with the version from `TestConstants.POSTGRES_MAJOR_VERSION` (currently `"16"`).
- The backend `TestLifecycleManager` installs the `pgcrypto` extension after container startup via raw JDBC; the aggregator one does not.
- The engine `TestLifecycleManager` also starts a Valkey container using `TestConstants.VALKEY_MAJOR_VERSION`.

## Mock Servers (WireMock)

- `MockServerLifecycleManager` in the `common` test module manages a shared WireMock server with both HTTP and HTTPS ports. Access it via `getMockServerUrl()`, `getMockServerHttpsUrl()`, and `getClient()`.
- `MockServerConfig` provides static helpers for configuring RBAC mock responses (`addMockRbacAccess`) using predefined `RbacAccess` enum payloads loaded from `rbac-examples/` resource files.
- Call `getClient().resetAll()` in `@BeforeEach` when a test configures its own WireMock stubs.

## Mocking CDI Beans

- Use `@InjectMock` (from `io.quarkus.test.InjectMock`) to fully replace a CDI bean with a Mockito mock.
- Use `@InjectSpy` (from `io.quarkus.test.junit.mockito.InjectSpy`) to spy on a real CDI bean while selectively overriding behavior.
- Prefer `@InjectMock`/`@InjectSpy` over manual `Mockito.mock()` for any CDI-managed dependency. Use manual mocks only for non-CDI objects.

## Reactive Messaging Tests

- Use `InMemoryConnector` from SmallRye to replace Kafka channels in tests. Inject it with `@Inject @Any InMemoryConnector`.
- `TestLifecycleManager.stop()` calls `InMemoryConnector.clear()` in the engine and aggregator modules. If writing a standalone messaging test, clear it in `@AfterEach`.

## Micrometer Metrics Testing

- Inject `MicrometerAssertionHelper` (`@ApplicationScoped`, in `common` test sources) to verify counter and timer increments.
- In `@BeforeEach`, call `saveCounterValuesBeforeTest(counterNames...)` for counters you will assert.
- After the operation, call `assertCounterIncrement(name, expectedDelta)` or the tag-filtered variants.
- For async assertions, use `awaitAndAssertCounterIncrement` or `awaitAndAssertTimerCountFilteredByTagsIncrement`, which use Awaitility with a 30-second timeout.
- Call `clearSavedValues()` in `@AfterEach` if reusing the helper across tests.
- Note: `MeterRegistry.clear()` must never be called because `@ApplicationScoped` beans hold references to registered meter instances.

## Connector Tests

### Legacy Camel-based connectors (connector-common)
- Extend `ConnectorRoutesTest` (which extends `CamelQuarkusTestSupport`). Override `getMockEndpointPattern()`, `getMockEndpointUri()`, `buildIncomingPayload()`, and `checkOutgoingPayload()`.
- Use `mockKafkaSourceEndpoint()`, `mockRemoteServerEndpoint()`, and `mockKafkaSinkEndpoint()` to wire up Camel route mocks.
- Send test messages via `sendMessageToKafkaSource(payload)`.

### V2 Quarkus-based connectors (connector-common-v2)
- Extend `BaseHttpConnectorIntegrationTest` (or `BaseConnectorIntegrationTest` for non-HTTP). Override `buildIncomingPayload()` and `getRemoteServerPath()`.
- The base class manages `InMemoryConnector` channels, WireMock setup, and metrics assertion helpers.
- Use `sendCloudEventMessage()`, `assertSuccessfulOutgoingMessage()`, and `assertFailedOutgoingMessage()` from the base class.

## Template Tests

See [Template Guidelines](template-guidelines.md) for the full test patterns (`EmailTemplatesRendererHelper`, `TestHelpers`, drawer/email/chat test organization).

## REST API Tests

- Use RestAssured's `given()` for HTTP endpoint tests. Serialize request bodies with `Json.encode()` (from `com.redhat.cloud.notifications.Json`), not `io.vertx.core.json.Json`.
- Create identity headers via `TestHelpers.createRHIdentityHeader()` and `TestHelpers.encodeRHIdentityInfo()`.
- For Kessel authorization tests, use `KesselTestHelper` (`@ApplicationScoped`) which builds `CheckRequest`/`CheckResponse` objects for mocked Kessel clients.

## Test Profiles

- Use `@TestProfile(ProdTestProfile.class)` to activate the `prod` Quarkus config profile during a test. Only use when testing prod-specific configuration behavior.

## Naming Conventions

- Suffix test classes with `Test` (e.g., `EndpointResourceTest`, `EventConsumerTest`).
- Use `ITest` suffix for integration tests that exercise the full lifecycle across multiple components (e.g., `LifecycleITest`).

## Verification

```shell
# Run all tests for a specific module
./mvnw test -pl backend

# Run a single test class
./mvnw test -pl backend -Dtest=EndpointResourceTest

# Run all tests across the project
./mvnw test

# Run tests with verbose output
./mvnw test -pl engine -Dtest=EventConsumerTest -Dsurefire.useFile=false
```
