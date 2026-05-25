# Code Organization Guidelines

## Module Architecture

This is a multi-module Maven project under `com.redhat.cloud.notifications` (parent artifact: `notifications-parent`). Modules fall into four categories:

### Deployable Applications (have `quarkus-maven-plugin`)
- **backend** -- Public-facing REST API (Integrations + Notifications APIs) with auth, RBAC, and Kessel
- **engine** -- Event processing pipeline: consumes Kafka events, resolves recipients, dispatches to connectors
- **aggregator** -- Scheduled daily digest email aggregation
- **recipients-resolver** -- Standalone service for resolving notification recipients via IT services, RBAC, MBOP, and Kessel
- **mcp** -- MCP (Model Context Protocol) server
- **connector-\*** (webhook, email, slack, google-chat, microsoft-teams, pagerduty, servicenow, splunk, drawer) -- Each is a standalone Quarkus app that receives CloudEvents from the engine via Kafka and delivers to external systems

### Shared Libraries (produce JARs with Jandex indexes for CDI discovery)
- **common** -- JPA entity models, shared request/response types, Constants, DB converters
- **common-template** -- Qute template definitions, extensions, and template files (`src/main/resources/templates/`)
- **common-unleash** -- Feature toggle utilities wrapping Unleash
- **database** -- Flyway migration scripts only (no Java code)

### Connector Framework Libraries
- **connector-common-v2**, **connector-common-http-v2**, **connector-common-authentication-v2** -- Current connector framework. All connectors except `connector-email` use v2
- **connector-common**, **connector-common-http**, **connector-common-authentication** -- Legacy v1 framework (only used by `connector-email`)

### Other
- **checkstyle** -- Shared Checkstyle configuration
- **admin-console** -- React/TypeScript webapp (built only with `-Padmin-console` profile)

## Module Dependencies

- Prefer depending on `common`, `common-unleash`, `database`, and `common-template` from deployable modules. Do not create circular dependencies between deployable apps
- Repositories (`db.repositories`) are defined per-module (backend and engine each have their own), not in `common`. Only DB converters, naming strategies, and JPA entities live in `common`
- New connectors must depend on the v2 framework (`connector-common-v2`, `connector-common-http-v2`, `connector-common-authentication-v2`). Avoid adding new code to the v1 framework modules
- Library modules that provide CDI beans to other modules must include the `jandex-maven-plugin` in their build
- Modules that share test utilities must produce a `test-jar` via `maven-jar-plugin` and consumers must declare the dependency in a `resolve-test-jars-if-tests-are-not-skipped` Maven profile

## Package Naming

All Java packages start with `com.redhat.cloud.notifications`. The next segment depends on module type:

| Module type | Package pattern | Example |
|---|---|---|
| Backend / Engine | `...{domain}` | `...routers.handlers.endpoint`, `...processors.email` |
| Common | `...models`, `...db.converters`, `...routers.models` | `...models.Endpoint` |
| Connector (v2) | `...connector.{name}` | `...connector.webhook`, `...connector.slack` |
| Connector framework | `...connector.v2` / `...connector.v2.http` | `...connector.v2.MessageHandler` |
| Recipients resolver | `...recipients.{domain}` | `...recipients.resolver.rbac` |

## Backend Package Structure

Place code in these packages within the `backend` module:

- `routers.handlers.{domain}` -- REST resource classes grouped by domain (e.g., `endpoint`, `notification`, `drawer`, `event`, `orgconfig`, `userconfig`, `status`)
- `routers.internal` -- Internal-only REST endpoints (not exposed to tenants, guarded by Turnpike)
- `routers` -- Cross-cutting JAX-RS infrastructure (exception mappers, interceptors, filters)
- `routers.models` -- Request/response POJOs specific to the backend API
- `db.repositories` -- Data access layer (one repository per aggregate root)
- `db.builder` -- Query builder utilities
- `config` -- Module-specific configuration class (`BackendConfig`)
- `auth` -- Authentication and authorization (RBAC, Kessel, principal extraction)
- `models.dto.v1` -- MapStruct DTO mappers and versioned DTOs for the public API
- `oapi` -- OpenAPI schema filtering (Integrations vs Notifications vs Internal)

## Engine Package Structure

- `events` -- Kafka consumers and event processing entry points
- `processors.{type}` -- Notification processor per integration type (e.g., `email`, `drawer`, `webhooks`, `pagerduty`, `camel.slack`)
- `db.repositories` -- Engine-specific repositories (separate from backend's)
- `config` -- Module-specific configuration class (`EngineConfig`)

## Connector Module Structure

Each `connector-{name}` module follows this layout:

- Package: `com.redhat.cloud.notifications.connector.{name}`
- Required class: `{Name}MessageHandler extends MessageHandler` -- override `handle()` to process incoming CloudEvents
- Optional: `{Name}RestClient` (Quarkus REST client interface), `{Name}ExceptionHandler`, `config/{Name}ConnectorConfig`
- Connector-specific models go in a `models` subpackage

## Configuration Class Naming

Each deployable module has one primary config class named `{Module}Config` (e.g., `BackendConfig`, `EngineConfig`, `RecipientsResolverConfig`). These are `@ApplicationScoped` beans using Unleash for feature toggles.

## API Path Constants

Define API base paths in `com.redhat.cloud.notifications.Constants`:
- `API_INTEGRATIONS_V_1_0` / `API_INTEGRATIONS_V_2_0`
- `API_NOTIFICATIONS_V_1_0` / `API_NOTIFICATIONS_V_2_0`
- `API_INTERNAL`

REST resources in `routers.handlers` use the public API paths. Resources in `routers.internal` use `API_INTERNAL`.

## Template File Organization

See [Template Guidelines](template-guidelines.md) for the full directory structure, naming conventions, and registration pattern.

## Flyway Migrations

All migration scripts live in `database/src/main/resources/db/migration/`. Naming convention:

```
V1.{sequence}.0__{JIRA-TICKET}_description.sql
```

Use the next available sequence number. Always include the Jira ticket ID when one exists.

## Import Order (Enforced by Checkstyle)

1. All third-party imports including `jakarta.*` (alphabetical)
2. `javax.*`
3. `java.*`
4. Static imports (separated, alphabetical)

Star imports are forbidden (except static). No `@author` Javadoc tags.

## Verification

```bash
# Validate checkstyle compliance across all modules
./mvnw validate --no-transfer-progress

# Compile all modules
./mvnw compile -DskipTests --no-transfer-progress

# Run tests for a single module
./mvnw test -pl backend --no-transfer-progress

# Run tests for a connector and its framework dependencies
./mvnw test -pl connector-webhook -am --no-transfer-progress

# Verify the full build
./mvnw clean verify --no-transfer-progress
```
