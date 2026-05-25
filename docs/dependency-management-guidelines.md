# Dependency Management Guidelines

## Version Declaration

- Declare shared dependency versions as properties in the root `pom.xml`. Child modules reference these via `${property.name}`.
- Declare module-specific versions (used by only one module) as properties in that module's `<properties>` block. Examples: `json-schema-validator-tests.version` in `backend`, `apache.commons.csv.version` in `engine`, `pushgateway.version` in `aggregator`.
- Reference inter-module dependencies with `${project.version}`, not a hardcoded version.

## Quarkus Platform BOM

- The root `pom.xml` imports `quarkus-bom` via `<dependencyManagement>`. All Quarkus-namespaced dependencies (`io.quarkus:*`) omit `<version>` in child modules because the BOM manages them.
- Quarkiverse extensions not covered by the BOM (e.g., `quarkus-logging-cloudwatch`, `quarkus-logging-sentry`, `quarkus-unleash`, `quarkus-mcp-server`) require explicit versions using root-level properties.

## Additional BOMs in Child Modules

- Modules using Apache Camel import `quarkus-camel-bom` in their own `<dependencyManagement>` section. This applies to `connector-common`, `connector-common-authentication`, `connector-common-http`, and `connector-email`. The v2 connector modules do not use Camel.
- The `mcp` module imports `quarkus-mcp-server-bom` in its own `<dependencyManagement>`.
- Use `${quarkus.platform.version}` for all platform-aligned BOM versions to keep them in sync with the Quarkus platform.

## Inter-Module Dependencies

- All modules use `notifications-parent` as their `<parent>` with `<relativePath>../pom.xml</relativePath>`.
- Internal module artifacts follow the naming convention `notifications-{module-name}` (e.g., `notifications-common`, `notifications-connector-email`).
- Shared library modules (`common`, `common-template`, `common-unleash`, `database`) are depended on by deployable modules (`backend`, `engine`, `aggregator`, connectors).
- Modules sharing test utilities produce test-jars via the `maven-jar-plugin` `test-jar` goal. Producers: `common`, `common-template`, `connector-common`, `connector-common-http`, `connector-common-v2`, `connector-common-http-v2`, `connector-common-authentication-v2`.

## Test-Jar Profile Workaround

- Modules consuming test-jars declare them inside a profile named `resolve-test-jars-if-tests-are-not-skipped`, activated when `maven.test.skip` is not `true`. This prevents build failures when compiling with `-Dmaven.test.skip`.
- When adding a new test-jar dependency to a connector or deployable module, place it inside this profile, not in the top-level `<dependencies>`.

## Automated Dependency Updates

- **Renovate (Mintmaker)**: Configured in `renovate.json`, extends `github>konflux-ci/mintmaker//config/renovate/renovate.json`. Targets the `master` branch. Tekton updates are set to automerge.
- **Dependabot**: Configured in `.github/dependabot.yml` for Maven (root `/`, daily), GitHub Actions (weekly), and npm (`/backend/src/main/webapp`, daily).
- **Renovate config validation**: A GitHub Actions workflow (`.github/workflows/renovate-config-validator.yaml`) validates `renovate.json` on PRs and pushes to `master`.
- Renovate PRs use conventional commit prefixes: `fix(deps):` for runtime dependency updates, `chore(deps):` for dev/build dependency updates.
- Quarkus platform version bumps arrive as Dependabot PRs titled `Bump quarkus.platform.version from X to Y`.

## Admin Console (Frontend)

- The `admin-console` module is behind the `admin-console` Maven profile and is not built by default.
- Frontend dependencies are managed via `yarn` with a lockfile at `admin-console/src/main/webapp/yarn.lock`. The `frontend-maven-plugin` handles `yarn install` and `yarn build` during the Maven build.
- Prefer pinned versions (no `^` or `~` prefix) for runtime dependencies in `package.json`. Dev dependencies may use range specifiers.

## Maven Enforcer Rules

- The root POM enforces minimum Maven version `3.9` and Java version `21` via the `maven-enforcer-plugin`. Builds fail if these are not met.
- Use the Maven wrapper (`./mvnw`) to ensure consistent Maven versions across environments.

## Adding a New Dependency

1. Check if the dependency is already managed by the Quarkus BOM -- if so, omit the version.
2. If shared across modules, add the version property to the root `pom.xml` `<properties>`.
3. If used by only one module, add the version property to that module's `<properties>`.
4. If the dependency belongs to an ecosystem with its own BOM (e.g., Camel), import that BOM in the module's `<dependencyManagement>` and omit the version.
5. Avoid hardcoding versions directly in `<dependency>` blocks -- use a property except for dependencies that are truly one-off and not expected to be updated.

## Verification

```bash
# Validate the full multi-module build compiles
./mvnw clean compile -DskipTests

# Check enforcer rules pass (Maven and Java version)
./mvnw enforcer:enforce

# List effective dependency versions for a module
./mvnw dependency:tree -pl backend

# Check for dependency convergence issues
./mvnw dependency:analyze -pl backend
```
