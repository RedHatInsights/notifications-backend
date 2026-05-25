# Configuration Guidelines

## Config Class Structure

- Place each module's config in a single `@ApplicationScoped` class named `{Module}Config` under a `config` package (e.g., `BackendConfig`, `EngineConfig`, `RecipientsResolverConfig`).
- Connector configs extend the inheritance chain: `ConnectorConfig` -> `HttpConnectorConfig` -> specific connector config (e.g., `EmailConnectorConfig`). Use `@Priority(BASE_CONFIG_PRIORITY + N)` to control bean resolution order.
- Declare property key names as `private static final String` constants at the top of the config class, grouped under an `Env vars configuration` comment block.
- Inject properties using `@ConfigProperty(name = CONSTANT_NAME, defaultValue = "...")` on package-private fields. Prefer providing a `defaultValue` for all non-secret properties.
- Use `Optional<T>` for properties that may genuinely be absent (see `RecipientsResolverConfig.kesselClientId` for the pattern).

## Property Naming

- Prefix all custom properties with `notifications.` (e.g., `notifications.kessel.enabled`, `notifications.unleash.enabled`).
- For connector-specific properties, use `notifications.connector.` prefix (e.g., `notifications.connector.kafka.incoming.topic`, `notifications.connector.name`).
- Use dot-separated kebab-case: `notifications.event-consumer.core-thread-pool-size`, not camelCase or underscores.

## Startup Logging

- Every config class must log its resolved values at startup using the `logConfigAtStartup(@Observes Startup event)` method (for non-connector modules) or by overriding `getLoggedConfiguration()` (for connector configs inheriting from `ConnectorConfig`).
- Log format: `Log.info("=== Startup configuration ===")` followed by `Log.infof("%s=%s", key, value)` for each entry, using a `TreeMap` for sorted output.
- Never log secret values (API tokens, passwords, client secrets). The `EmailConnectorConfig` pattern includes explicit warning comments: `/* /!\ WARNING /!\ DO NOT log config values that come from OpenShift secrets. */`.

## Secrets Handling

- Use placeholder values like `PLACEHOLDER`, `FILL_ME`, `REPLACE_ME_FROM_ENV_VAR`, or `changeme` in `application.properties` for secrets that are injected from environment variables or OpenShift secrets at deployment.
- Never commit real secret values. PSK development values (e.g., `sources.psk=development-value-123`) are acceptable only for local development.
- Secrets injected from Clowder use the `${clowder.endpoints.<service>.<property>}` expression syntax with a local fallback: `${clowder.endpoints.rbac-service.url:https://ci.cloud.redhat.com}`.

## Feature Flags (Unleash)

### Toggle Registration

- Register toggles in `@PostConstruct` via `ToggleRegistry.register(featureName, logChanges)`. This generates the toggle name as `{quarkus.application.name}.{featureName}.enabled`.
- Store the returned toggle name in a `private String` field at the top of the config class, grouped under an `Unleash configuration` comment block.
- Always pass `true` for `logChanges` so `ToggleChangedLogger` tracks state transitions.

### Toggle Evaluation Pattern

- Use the dual-path pattern: check `unleashEnabled` first, then call `unleash.isEnabled(toggleName, context, defaultValue)`. The `unleashEnabled` boolean acts as a kill switch.
- For org-scoped toggles, build context via `UnleashContextBuilder.buildUnleashContextWithOrgId(orgId)`. For custom context properties (e.g., `endpointId`, `method_and_path`), build `UnleashContext` inline.
- Default the fallback value to `false` unless the feature should be on when Unleash is unreachable.

```java
public boolean isFeatureEnabled(String orgId) {
    if (unleashEnabled) {
        UnleashContext ctx = UnleashContextBuilder.buildUnleashContextWithOrgId(orgId);
        return unleash.isEnabled(featureToggle, ctx, false);
    } else {
        return featureEnabledFallback; // @ConfigProperty fallback
    }
}
```

### Unleash Infrastructure (common-unleash module)

- `UnleashConfigSource` provides shared defaults (`quarkus.unleash.name-prefix=notifications`, synchronous init) at ordinal 240 (below `application.properties`). It is registered as a MicroProfile `ConfigSource` via `META-INF/services`.
- `Subscriber` fires CDI events on toggle changes, consumed by `ToggleChangedLogger` (logs state transitions) and `LogLevelManager` (adjusts log levels via Unleash variants).
- `PodRestartRequestedChecker` uses an Unleash variant payload to signal pod restarts by hostname.

### Deprecation of Config-Based Fallbacks

- Config properties that serve as Unleash fallbacks are annotated `@Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")`. Do not add new fallback properties; add new toggles via Unleash only.

## application.properties Conventions

- Each deployable module has its own `src/main/resources/application.properties`. Shared library modules (`common`, `common-template`, `connector-common`) keep minimal or empty properties files.
- Use Quarkus profile prefixes for environment-specific overrides: `%test.`, `%dev.`. Reserve `%prod.` for production-only settings.
- Set `quarkus.unleash.active=false` and `quarkus.unleash.url=http://localhost:4242` in every module's properties to disable Unleash locally.
- Use `quarkus.http.port` with unique ports per module to avoid conflicts during local development (backend=8085, engine=8087, recipients-resolver=9008, connectors=9001+).

## Config Interceptors

- Use `ConfigSourceInterceptor` (registered in `META-INF/services/io.smallrye.config.ConfigSourceInterceptor`) to transform resolved config values at load time. See `KesselConfigInterceptor` which strips HTTP schemes from gRPC URLs.

## Test-Only Config Overrides

- Guard runtime config setters with `LaunchMode.current().isDevOrTest()` checks to prevent accidental config changes in production. Throw `IllegalStateException` if called outside test/dev mode.

## Verification

```shell
# Confirm all modules disable Unleash locally
grep -r "quarkus.unleash.active=false" */src/main/resources/application.properties

# Find config classes missing startup logging
grep -rL "logConfigAtStartup\|getLoggedConfiguration" */src/main/java/**/config/*Config.java

# Check for secrets accidentally committed (should only find placeholders)
grep -rn "password\|secret\|api_token\|psk" */src/main/resources/application.properties | grep -v "trust-store-password" | grep -v "PLACEHOLDER\|FILL_ME\|REPLACE_ME\|changeme\|development-value\|placeholder"

# Verify toggle registrations use logChanges=true
grep -rn "toggleRegistry.register" */src/main/java --include="*.java" | grep -v "true)"
```
