# Security Guidelines

## Authentication Architecture

### Backend: ConsoleAuthMechanism

The backend module uses `ConsoleAuthMechanism` (implements `HttpAuthenticationMechanism`) to authenticate
requests via the `x-rh-identity` header. When adding new API paths:

- Annotate user-facing endpoints with `@Authorization` -- never `@RolesAllowed` (reserved for internal endpoints).
- The `@Authorization` annotation requires both `legacyRBACRole` and `workspacePermissions` attributes to support
  the RBAC-to-Kessel migration.
- Every `@Authorization`-annotated method must accept a `SecurityContext` parameter; the `AuthorizationInterceptor`
  will throw `IllegalStateException` if it is missing.

```java
@Authorization(
    legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS,
    workspacePermissions = INTEGRATIONS_VIEW
)
public Response listEndpoints(@Context SecurityContext sec, ...) { }
```

### MCP Module: McpAuthMechanism

The MCP module has its own `McpAuthMechanism` and `McpIdentityProvider` -- separate from the backend auth stack.
Key differences from the backend:

- Only `User` identity types are accepted; service accounts are rejected.
- Requires `org_id`, `user_id`, and `username` -- all three must be present and non-blank.
- No RBAC or Kessel permission checks; the identity is passed through to backend API calls.
- Anonymous paths are limited to `/q/health` and `/q/metrics` (not the same paths as the backend).
- Track auth outcomes via Micrometer counters (`notifications.mcp.auth.*`) with `reason` tags.

### Paths Allowed Without Authentication (Backend)

The following paths bypass `x-rh-identity` validation in `ConsoleAuthMechanism.authenticate()`:

- `*/openapi.json` on any API path
- `/openapi.json`, `/health`, `/metrics`
- `/internal/validation`, `/internal/gw`, `/internal/version`

Do not add new unauthenticated paths without updating the allowlist in `ConsoleAuthMechanism.authenticate()`.

## Authorization: Dual RBAC / Kessel Model

The codebase supports two authorization backends simultaneously, toggled per org via `BackendConfig`:

1. **Legacy RBAC** -- roles fetched from the external RBAC server and cached (`rbac-cache`).
   Uses `securityContext.isUserInRole(role)` checks with role constants from `ConsoleIdentityProvider`.
2. **Kessel** -- gRPC-based permission checks via `KesselInventoryAuthorization`.
   Uses `CheckOperation.CHECK` for read operations and `CheckOperation.UPDATE` for write operations.

When Kessel is enabled for an org, RBAC roles are not fetched. When writing new endpoints,
always specify both `legacyRBACRole` and `workspacePermissions` in the `@Authorization` annotation.

### RBAC Role Constants

Use the constants defined in `ConsoleIdentityProvider` -- never hardcode role strings:

| Constant | Value | Usage |
|---|---|---|
| `RBAC_READ_NOTIFICATIONS_EVENTS` | `read:events` | Event log viewing |
| `RBAC_READ_NOTIFICATIONS` | `read:notifications` | Notification config reading |
| `RBAC_WRITE_NOTIFICATIONS` | `write:notifications` | Notification config writing |
| `RBAC_READ_INTEGRATIONS_ENDPOINTS` | `read:integrations_ep` | Integration reading |
| `RBAC_WRITE_INTEGRATIONS_ENDPOINTS` | `write:integrations_ep` | Integration writing |
| `RBAC_INTERNAL_USER` | `read:internal` | Turnpike-authenticated users |
| `RBAC_INTERNAL_ADMIN` | `write:internal` | Admin group members |

### Kessel Workspace Permissions

Use the `WorkspacePermission` enum values in `@Authorization` annotations:

- `EVENTS_VIEW`, `INTEGRATIONS_VIEW`, `NOTIFICATIONS_VIEW` -- mapped to `CheckOperation.CHECK`
- `INTEGRATIONS_EDIT`, `NOTIFICATIONS_EDIT` -- mapped to `CheckOperation.UPDATE`

### Internal API Authorization

Internal endpoints (`/internal/*`) use `@RolesAllowed` with `RBAC_INTERNAL_ADMIN` or `RBAC_INTERNAL_USER`.
Prefer class-level `@RolesAllowed(RBAC_INTERNAL_ADMIN)` with method-level overrides for read-only methods.

For fine-grained internal access, use `SecurityContextUtil.hasPermissionForRole()` or
`hasPermissionForApplication()`, which check `InternalRoleAccess` records (prefixed with `internal-role:`).

## Identity Types

`ConsoleIdentityProvider` handles three identity types from the `x-rh-identity` header:

- **RhIdentity** (User/ServiceAccount) -- requires `org_id`; Kessel additionally requires `user_id`.
- **TurnpikeSamlIdentity** (Associate) -- for internal API access. Roles from the SAML assertion
  are prefixed with `internal-role:` via `InternalRoleAccess.getInternalRole()`.
- All other identity types are rejected with `AuthenticationFailedException`.

Avoid leaking authentication implementation details in error responses. Return empty bodies
on 401 responses (verified in `ConsoleIdentityProviderTest`).

## Service-to-Service Authentication

### RBAC Service Calls (recipients-resolver)

The `RbacServiceToService` interface has two implementations following the dual-client pattern:

- **OIDC** (`RbacServiceToServiceOidc`): annotated with `@OidcClientFilter`, config key `rbac-s2s-oidc`.
- **PSK** (`RbacServiceToServicePsk`): uses `AuthRequestFilter` to inject `x-rh-rbac-psk` and
  `x-rh-rbac-client-id` headers, config key `rbac-s2s`.

Both pass `x-rh-rbac-org-id` for tenant scoping.

### RBAC Workspace Calls (backend)

- **OIDC** (`RbacWorkspacesOidcClient`): config key `rbac-authentication-oidc`, uses `@OidcClientFilter`.
- **PSK** (`RbacWorkspacesPskClient`): config key `rbac-authentication`, sends `x-rh-rbac-psk` and
  `x-rh-rbac-client-id` headers.

### Sources API Calls (connector-common-authentication)

Two modules exist: `connector-common-authentication` (Camel-based) and `connector-common-authentication-v2`.
Both follow the same OIDC/PSK dual-client pattern:

- **OIDC** (`SourcesOidcClient`): config key `sources-oidc`, uses `@OidcClientFilter`.
- **PSK** (`SourcesPskClient`): config key `sources`, sends `x-rh-sources-psk` header.

The selection between OIDC and PSK is per-org via `ConnectorConfig.isSourcesHccClusterEnabled()`.

## Security Testing

### Backend Auth Tests

- Use `TestHelpers.encodeRHIdentityInfo()` to create user identities.
- Use `TestHelpers.encodeRHServiceAccountIdentityInfo()` for service account identities.
- Use `TestHelpers.encodeTurnpikeIdentityInfo()` for internal/Turnpike identities.
- Mock RBAC responses with `MockServerConfig.addMockRbacAccess()` using the `RbacAccess` enum
  (`FULL_ACCESS`, `READ_ACCESS`, `NO_ACCESS`, etc.).
- Invalidate `rbac-cache` between test cases that reuse the same identity header.

### MCP Auth Tests

- Use `McpTestHelpers.encodeRHIdentityInfo()` (the MCP module has its own test helper).
- Verify auth metric increments with `MicrometerAssertionHelper`.
- Test rejection of service account identities, missing fields, and malformed headers.

### Auth Test Assertions

Always verify that 401 responses have empty bodies to prevent leaking security implementation details:
```java
.statusCode(HttpStatus.SC_UNAUTHORIZED)
.body(emptyString());
```

## Verification

```bash
# Run backend auth tests
./mvnw test -pl backend -Dtest=ConsoleIdentityProviderTest,AuthenticationTest,AuthorizationInterceptorTest

# Run MCP auth tests
./mvnw test -pl mcp -Dtest=McpAuthTest

# Run Kessel authorization tests
./mvnw test -pl backend -Dtest=KesselInventoryAuthorizationTest,KesselCheckClientTest

# Verify no hardcoded role strings (should use ConsoleIdentityProvider constants)
grep -rn '"read:events"\|"read:notifications"\|"write:notifications"\|"read:integrations_ep"\|"write:integrations_ep"' \
  backend/src/main/java/ --include="*.java" | grep -v ConsoleIdentityProvider.java
```
