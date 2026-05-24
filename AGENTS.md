# AGENTS.md

## Project Overview

Notifications Backend is a multi-module Quarkus (3.35.x) application that powers the Red Hat Hybrid Cloud Console notification system. It processes events from the platform, resolves recipients, renders templates, and delivers notifications through various channels (email, webhook, Slack, Microsoft Teams, PagerDuty, ServiceNow, Splunk, Google Chat, drawer). The codebase is Java 21, uses PostgreSQL with Flyway migrations, communicates over Kafka with CloudEvents, and deploys to OpenShift via Konflux/Tekton pipelines.

## Build and Test Commands

```sh
# Full build with checkstyle and tests (all modules)
./mvnw clean verify --no-transfer-progress

# Build a single deployable module and its dependencies
./mvnw clean verify -pl :notifications-backend -am --no-transfer-progress

# Build including the admin-console (React UI, only when admin-console/ changed)
./mvnw clean verify -Padmin-console -pl :checkstyle,:notifications-admin-console --no-transfer-progress

# Run a single test class
./mvnw test -pl :notifications-backend -Dtest=EndpointRepositoryTest --no-transfer-progress

# Skip tests (for quick compilation check)
./mvnw clean package -DskipTests --no-transfer-progress
```

Checkstyle runs during the `validate` phase and will fail the build on violations. Fix all checkstyle errors before pushing.

## Cross-Cutting Conventions

### Java and Jakarta EE

- Java 21 is required (enforced by maven-enforcer-plugin).
- Use `jakarta.*` packages, not `javax.*` (the project has fully migrated). The only `javax.*` imports still present are for `javax.annotation.Nullable` and JMX, which are not part of Jakarta EE.
- No Lombok. All classes use standard Java code.
- Use `io.quarkus.logging.Log` (static import) for logging, not `org.jboss.logging.Logger`. The entire backend uses the Quarkus static Log pattern.
- Use field injection with `@Inject` (not constructor injection). This is the established pattern throughout the codebase.

### Checkstyle Rules (enforced on all modules including tests)

- No tab characters; use 4-space indentation.
- No trailing whitespace; newline required at end of file.
- No `@author` Javadoc tags.
- No star imports (except static member imports).
- No JUnit 4 imports (`junit.framework.*`, `org.junit.Test`, etc.); use JUnit 5 (`org.junit.jupiter`).
- Import order: all other packages first, then `javax`, then `java`, then static imports in a separate group at the bottom, sorted alphabetically.
- Braces required on all control structures; opening brace on same line (K&R style).
- Generated sources under `target/generated-sources/` are excluded from checks.

### Naming Conventions

- Entity fields use Java camelCase; the `SnakeCasePhysicalNamingStrategy` in `common` translates to snake_case column names automatically. Do not use `@Column(name=...)` for simple name mapping.
- Flyway migrations follow `V1.{sequence}.0__{JIRA-KEY}_description.sql` in the `database` module.
- REST resource classes live under `routers/handlers/{domain}/` and are named `{Domain}Resource.java`.
- Repository classes live under `db/repositories/` and are named `{Entity}Repository.java`.
- DTOs live under `models/dto/v1/` (or `v2/` when versioned) and use MapStruct mappers.

### API Versioning Pattern

Public resources use inner static subclasses for versioning. The parent class holds the shared implementation; version-specific behavior overrides methods in the inner class. See `README.adoc` for the canonical example. Path constants are defined in `com.redhat.cloud.notifications.Constants`.

### Multi-Module Dependency Rules

- Shared libraries (`common`, `common-template`, `common-unleash`, `connector-common*`) produce JARs and must not depend on deployable modules.
- The `database` module contains only Flyway SQL migration files -- no Java code.
- Only `backend` runs Flyway migrations in production (`quarkus.flyway.migrate-at-start=true`). The `engine` enables Flyway only for dev/test profiles.
- Connector modules follow a layered inheritance: `connector-common` -> `connector-common-http` -> specific connector. The v2 equivalents (`connector-common-v2`, `connector-common-http-v2`) are the newer framework.

### Feature Toggles

Unleash is used for feature flags. It is disabled by default locally (`quarkus.unleash.active=false`) and activated in deployed environments. Feature toggle utilities are in the `common-unleash` module.

## Detailed Guidelines

For domain-specific conventions, refer to these guideline documents:

1. [Code Organization](docs/code-organization-guidelines.md) -- Module architecture, packages, Flyway, import order
2. [API Contracts](docs/api-contracts-guidelines.md) -- REST conventions, versioning, DTOs, pagination, OpenAPI
3. [Database](docs/database-guidelines.md) -- Schema, Flyway, entity conventions, repository patterns
4. [Data Validation](docs/data-validation-guidelines.md) -- Bean Validation, DTOs, MapStruct, JSON serialization
5. [Security](docs/security-guidelines.md) -- ConsoleAuthMechanism, @Authorization, RBAC/Kessel
6. [Testing](docs/testing-guidelines.md) -- @QuarkusTest, DbIsolatedTest, WireMock, connector tests
7. [Async and Messaging](docs/async-and-messaging-guidelines.md) -- Kafka, connector frameworks, CloudEvents
8. [Configuration](docs/configuration-guidelines.md) -- Config classes, property naming, Unleash, secrets
9. [Error Handling](docs/error-handling-guidelines.md) -- JAX-RS mappers, connector retry, DelayedThrower
10. [Integration](docs/integration-guidelines.md) -- v2 connector components, REST clients, Sources auth
11. [Logging and Observability](docs/logging-and-observability-guidelines.md) -- Logging, Micrometer metrics, health checks
12. [Performance](docs/performance-guidelines.md) -- @Blocking, backpressure, caches, deduplication
13. [Dependency Management](docs/dependency-management-guidelines.md) -- Maven BOMs, inter-module deps, Renovate
14. [Deployment](docs/deployment-guidelines.md) -- Dockerfiles, Tekton/Konflux, Clowder, GitHub Actions

## CI/CD

- **GitHub Actions**: `build.yml` runs `./mvnw clean verify` on every push/PR. It also builds the admin-console when `admin-console/` files change and uploads OpenAPI spec artifacts.
- **Tekton/Konflux**: `.tekton/` contains per-module PipelineRun definitions for pull-request and push events against `master`. These build container images using the Dockerfiles in `docker/`.
- **Dependabot**: Configured for Maven, GitHub Actions, and npm (admin-console) dependency updates.
- **Renovate**: Configured via `renovate.json` for Tekton pipeline reference updates with auto-merge.
- **CodeQL**: Security scanning runs via `.github/workflows/codeql-analysis.yml`.

## Key Gotchas

- The `database` module has no Java code -- it exists solely to hold Flyway migration SQL files that are picked up by other modules at build time via classpath.
- Connectors are independent deployable services, not libraries. Each has its own `application.properties`, Dockerfile, and Tekton pipeline.
- The Clowder Config Source translates OpenShift Clowder environment configuration into Quarkus properties. Locally, fallback defaults in `application.properties` use `${clowder.endpoints...url:http://localhost:...}` syntax.
- OpenAPI specs are generated per API version during the build and uploaded as CI artifacts. They are filtered by the `OApiFilter` class.
- The `admin-console` module is behind the `admin-console` Maven profile and is not built by default.
- Backend runs on port 8085, engine on 8087 (tests use 9085/9087 respectively) to avoid conflicts when running locally.
