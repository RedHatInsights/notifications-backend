# Security Guidelines

Rules and conventions for security in `notifications-backend`, derived from existing patterns. All agents MUST follow these during implementation and review.

## 1. Authentication Architecture

The system supports three authentication paths, selected at runtime:

### 1.1 x-rh-identity Header (Primary)
- All public API requests must include the `x-rh-identity` Base64-encoded header. Requests without it are rejected with `AuthenticationFailedException`, except for health/metrics/openapi endpoints.
- The header is decoded and deserialized into a `ConsoleIdentity` subclass (`RhIdentity` for users/service accounts, `TurnpikeSamlIdentity` for internal associates).
- `org_id` is mandatory on `RhIdentity`; requests without it are rejected.
- Authentication logic lives in `ConsoleAuthMechanism` (mechanism) and `ConsoleIdentityProvider` (identity provider). Do not bypass this flow.

### 1.2 Turnpike (Internal API)
- Internal APIs (`/api/internal/`) use Turnpike SAML "associate" identities.
- These identities automatically receive the `read:internal` role.
- The `write:internal` (admin) role is granted only to associates whose roles include the value of the `internal.admin-role` config property (currently `crc-notifications-team`).
- When `internal-rbac.enabled=false`, all internal requests get full admin privileges -- this is a dev-only bypass. Never enable this in production.

### 1.3 Kessel vs RBAC (Dual Authorization Backend)
- The system supports two authorization backends simultaneously: legacy RBAC and Kessel.
- Kessel enablement is checked **per org_id** via Unleash feature toggle, allowing gradual rollout.
- When Kessel is enabled for an org, RBAC is bypassed entirely; the security identity is built with no roles (Kessel checks happen at the method level).
- When Kessel is enabled, the `user_id` field in the identity header is mandatory (used as the Kessel subject).
- If both Kessel and RBAC are disabled in a non-local environment, authentication fails hard with an error log.

## 2. Authorization Patterns

### 2.1 The `@Authorization` Annotation (Public API)
- Public-facing endpoint methods typically use the `@Authorization` annotation, which serves both RBAC and Kessel:
  ```java
  @Authorization(
      legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS,
      workspacePermissions = INTEGRATIONS_VIEW
  )
  ```
- The `legacyRBACRole` is checked when RBAC is active (role was pre-fetched during authentication).
- The `workspacePermissions` array is checked when Kessel is active (calls Kessel at request time).
- Both fields must always be specified. If `workspacePermissions` is empty when Kessel is active, the interceptor throws `IllegalStateException`.
- The annotated method **must** have a `SecurityContext` parameter; the interceptor throws `IllegalStateException` otherwise.
- **Exception**: Some endpoints marked with `@Tag(name = OApiFilter.PRIVATE)` (e.g., `UserConfigResource`, `DrawerResource`, `StatusResource`, `UserPreferencesForPolicy`) extract orgId/username from SecurityContext but do not use `@Authorization`. These endpoints rely on the underlying authentication mechanism without explicit authorization checks.

### 2.2 RBAC Role Constants
Use only the predefined constants from `ConsoleIdentityProvider`:
- `RBAC_READ_NOTIFICATIONS_EVENTS` -- read events
- `RBAC_READ_NOTIFICATIONS` / `RBAC_WRITE_NOTIFICATIONS` -- read/write notification configs
- `RBAC_READ_INTEGRATIONS_ENDPOINTS` / `RBAC_WRITE_INTEGRATIONS_ENDPOINTS` -- read/write integrations
- `RBAC_INTERNAL_USER` / `RBAC_INTERNAL_ADMIN` -- internal API access

### 2.3 Kessel Workspace Permissions
Defined in `WorkspacePermission` enum. Map to Kessel schema names:
- `EVENTS_VIEW` -> `notifications_events_view`
- `INTEGRATIONS_VIEW` / `INTEGRATIONS_EDIT` -> `integrations_endpoints_view` / `integrations_endpoints_edit`
- `NOTIFICATIONS_VIEW` / `NOTIFICATIONS_EDIT` -> `notifications_notifications_view` / `notifications_notifications_edit`

Kessel uses `CheckOperation.CHECK` for view permissions and `CheckOperation.UPDATE` for edit permissions. The `UPDATE` check has side effects in Kessel's inventory -- always use the correct operation type.

### 2.4 `@RolesAllowed` (Internal API Only)
- Internal API resources use `@RolesAllowed` with `RBAC_INTERNAL_USER` or `RBAC_INTERNAL_ADMIN`.
- Write/mutate operations on internal resources require `RBAC_INTERNAL_ADMIN`.
- Read operations on internal resources use `RBAC_INTERNAL_USER`.
- Some internal resources also use `SecurityContextUtil.hasPermissionForRole()` or `hasPermissionForApplication()` for fine-grained role-based access tied to specific applications.

## 3. Tenant Isolation

### 3.1 Mandatory orgId Scoping
- All database queries for tenant-scoped data **must** include `orgId` in the WHERE clause.
- The `orgId` is extracted from the security context via `SecurityContextUtil.getOrgId(securityContext)`.
- Repository methods accept `orgId` as an explicit parameter (e.g., `getEndpoint(String orgId, UUID id)`).
- Never trust client-supplied orgId values; always extract from the authenticated identity.

### 3.2 Pattern in Repositories
```java
String hql = "SELECT e FROM Endpoint e WHERE e.orgId = :orgId AND e.id = :id";
query.setParameter("orgId", orgId);
```
Every new repository method that accesses tenant data must follow this pattern.

## 4. SSRF Prevention

### 4.1 `@ValidNonPrivateUrl` Custom Validator (DTO/API Layer)
- All user-supplied webhook/camel URLs **must** be annotated with `@ValidNonPrivateUrl`.
- The backing validator `ValidNonPrivateUrlValidator` (in `common` module) checks at **DTO/API validation time**:
  - URL is well-formed (valid URL and URI)
  - Scheme is `http` or `https` only (rejects `ftp://`, etc.)
  - Hostname does not resolve to a private/site-local IP (uses `InetAddress.isSiteLocalAddress()` which covers 192.168.x.x, 172.16-31.x.x, 10.x.x.x ranges)
  - Hostname does not resolve to a loopback address (127.x.x.x, localhost) in non-dev/test modes
  - Hostname resolves to a known host
- Applied on: `WebhookPropertiesDTO.url`, `CamelPropertiesDTO.url`
- New endpoint types that accept user-supplied URLs must use this annotation.

### 4.1.1 Connector-Side URL Validation
- `connector-common-http`'s `UrlValidator.java` (`UrlValidator.validateTargetUrl()`) performs **scheme-only validation** (requires `https`) at connector execution time. It does **not** perform hostname resolution, private IP, or loopback checks.
- If full private-network/loopback SSRF protections are required at the connector layer (e.g. to guard against DNS rebinding or TOCTOU between DTO validation and connector execution), host/IP checks must be implemented separately in the connector pipeline.

### 4.2 Webhook HTTP Method Restriction
- `WebhookPropertiesDTO` restricts the HTTP method to POST only via `@AssertTrue` validation on `isHttpMethodAllowed()`.

## 5. Secrets Management

### 5.1 External Secrets Storage (Sources)
- Secrets (bearer tokens, secret tokens) are **never stored in the notifications database**.
- They are stored in the external Sources service and referenced by ID (`secretTokenSourcesId`, `bearerAuthenticationSourcesId`).
- The `SourcesSecretable` interface marks endpoint property classes that manage secrets via Sources.
- `SecretUtils` handles the full lifecycle: create, read, update, delete secrets in Sources.

### 5.2 Dual Authentication to Sources
- Sources supports two authentication methods, toggled per org via Unleash (`sources-oidc-auth`):
  - **PSK**: Pre-shared key sent in `x-rh-sources-psk` header. Key is in `sources.psk` config property.
  - **OIDC**: OAuth2 client credentials flow via `@OidcClientFilter` annotation on the REST client.
- PSK client uses internal Sources endpoints (`/internal/v2.0/secrets/{id}`).
- OIDC client uses the same internal endpoints but authenticates via bearer token.

### 5.3 Configuration Secrets
- OIDC client credentials (`quarkus.oidc-client.client-id`, `quarkus.oidc-client.credentials.secret`) use placeholder values in `application.properties` and must be injected via environment variables.
- Kessel OAuth2 credentials are cached with 7-day TTL (`kessel-oauth2-client-credentials` cache).
- Never commit real secret values to `application.properties`.

## 6. Input Validation Conventions

### 6.1 DTO Validation
- All request body parameters must use `@NotNull @Valid` on endpoint methods. The `@RequestBody` annotation is optional and used only for OpenAPI documentation.
- DTOs use Jakarta Bean Validation annotations:
  - `@NotNull` for required fields
  - `@Size(max = N)` for string length limits (e.g., 255 for names, 32 for tokens, 20 for sub-types)
  - `@Pattern(regexp = "[a-z][a-z_0-9-]*")` for slug-like identifiers (bundles, applications, event types)
  - `@Min(0)` for non-negative numeric fields
  - `@ValidNonPrivateUrl` for user-supplied URLs (see SSRF section)
- Nested properties use `@Valid` to trigger cascading validation (e.g., `EndpointDTO.properties`).

### 6.2 Query Parameters
- Use `@Valid @BeanParam Query` for paginated list endpoints.

## 7. Kessel gRPC Client Resilience

The `KesselCheckClient` implements specific resilience patterns:
- **Timeout**: All gRPC calls use a configurable deadline (`notifications.kessel.timeout-ms`, default 30000ms).
- **Retry**: 3 retries with 100ms delay on transient failures (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `ABORTED`).
- **Token refresh**: On `UNAUTHENTICATED` errors, the gRPC channel is recreated with fresh OAuth2 credentials (cache is cleared).
- **Channel health**: If the gRPC channel enters `SHUTDOWN` state, it is automatically recreated.
- Non-transient errors (`PERMISSION_DENIED`, `NOT_FOUND`) are not retried and propagate immediately.

## 8. RBAC Caching and Retry

- RBAC responses are cached for 120 seconds (`rbac-cache`).
- RBAC calls retry up to 3 times with exponential backoff (0.1s initial, 1s max) on `IOException` and `ConnectTimeoutException`.
- REST client timeouts: 2000ms connect, 2000ms read.

## 9. Security Review Checklist

When reviewing or implementing changes:

1. Public endpoint methods should have `@Authorization` with both `legacyRBACRole` and `workspacePermissions` (exceptions include endpoints marked with `@Tag(name = OApiFilter.PRIVATE)`).
2. Every internal endpoint method has `@RolesAllowed` with appropriate admin/user role.
3. Every database query for tenant data includes `orgId` in the WHERE clause.
4. User-supplied URLs use `@ValidNonPrivateUrl`.
5. Request body DTOs use `@NotNull @Valid` and have appropriate size/pattern constraints.
6. Secrets are stored in Sources, not in the local database.
7. No real credentials appear in `application.properties` (only placeholders).
8. New Kessel permissions are added to the `WorkspacePermission` enum and the `getCheckOperation` method.
9. The `SecurityContext` parameter is present on methods using `@Authorization`.
