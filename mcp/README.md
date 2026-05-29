# Notifications MCP Server

MCP (Model Context Protocol) server for Red Hat Hybrid Cloud Console Notifications service.

## Authentication

Per [ADR-084](https://github.com/openshift-online/architecture/pull/67), this MCP server authenticates using the `x-rh-identity` header rather than standard MCP auth headers, since the HCC gateway strips and transforms authentication headers.

### x-rh-identity Header Validation

All requests to `/mcp/*` endpoints **must** include a valid `x-rh-identity` header. Requests without this header are rejected with HTTP 401.

The authentication mechanism validates:

1. **Header presence** - Requests must include `x-rh-identity`
2. **Base64 encoding** - Header value must be valid Base64
3. **JSON structure** - Decoded value must be valid JSON matching the ConsoleIdentityWrapper format
4. **Identity type** - Only `User` identities are accepted. Service accounts and other types are rejected.
5. **Required fields**:
   - `org_id` - **Mandatory**, must be present and non-empty
   - `user_id` - **Mandatory**, must be present and non-empty
   - `username` - **Mandatory**, must be present and non-empty

### Security Guarantees

✅ **User-only access** - Only User identities are accepted; service accounts are rejected  
✅ **Identity inheritance** - Tools operate with the same RBAC permissions as the authenticated user  
✅ **Gateway validation** - The HCC gateway already handles OIDC token validation before forwarding requests  
✅ **Tenant isolation** - Each request carries orgId for multi-tenant data access

## MCP Tools

All tools require a valid `x-rh-identity` header (enforced by `McpAuthMechanism` at the HTTP level).

### `serverInfo`

Returns server status and version information. Does not use identity data.

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 1,
  "params": {
    "name": "serverInfo",
    "arguments": {}
  }
}
```

### `whoami`

Returns information about the authenticated user from the x-rh-identity header.

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 2,
  "params": {
    "name": "whoami",
    "arguments": {}
  }
}
```

**Response:**
```text
Authenticated as:
  Organization ID: 12345
  User ID: user-abc-123
  Username: jdoe
```

### `getSeverities`

Returns the list of available notification severities. Calls the backend API via HTTP REST client, forwarding the caller's `x-rh-identity` header.

Equivalent REST endpoint: `GET /api/notifications/v2.0/notifications/severities`

**Example:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 4,
  "params": {
    "name": "getSeverities",
    "arguments": {}
  }
}
```

**Response:**
```text
["CRITICAL","IMPORTANT","MODERATE","LOW","NONE","UNDEFINED"]
```

### Integration Management

The MCP server provides full CRUD operations for integration endpoints via `IntegrationTools`. Tools include:
- **Read**: `getIntegrations`, `getIntegration`, `getIntegrationHistory`, `getIntegrationHistoryDetails`
- **Write**: `createIntegration`, `updateIntegration`, `enableIntegration`, `disableIntegration`, `testIntegration`, `deleteIntegration`

Integration endpoints use **polymorphic DTOs** (see `dto/EndpointDTO.java`). The `type` field determines which properties structure is used:
- `webhook`, `ansible` → WebhookPropertiesDTO
- `camel` → CamelPropertiesDTO (requires `sub_type`: slack, teams, google_chat, servicenow, splunk)
- `pagerduty` → PagerDutyPropertiesDTO
- `drawer`, `email_subscription` → SystemSubscriptionPropertiesDTO

Each tool includes detailed parameter descriptions via `@Tool` annotations. Use the MCP `tools/list` method to see complete schemas.

## Testing

### Running Tests

```bash
./mvnw test -pl mcp
```

### Test Coverage

- ✅ Valid x-rh-identity header authentication
- ✅ Missing x-rh-identity header rejection (401)
- ✅ Malformed x-rh-identity header rejection (401)
- ✅ Missing or empty org_id rejection (401)
- ✅ Missing or empty user_id rejection (401)
- ✅ Missing or empty username rejection (401)
- ✅ Service account identity rejection (401)
- ✅ Authenticated tool access with valid identity (serverInfo, whoami)
- ✅ Tools list returns registered tools
- ✅ Tools list without identity is rejected (401)
- ✅ Health endpoint does not require authentication
- ✅ Micrometer counter assertions for auth success/failure

### Manual Testing

**Important:** The MCP Streamable HTTP transport requires `Accept: application/json, text/event-stream` on all requests.

```bash
# Initialize MCP session
curl -X POST http://localhost:9010/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "x-rh-identity: <base64-encoded-identity>" \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "id": 1,
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {},
      "implementation": {
        "name": "my-client",
        "version": "1.0.0"
      }
    }
  }'

# List available tools
curl -X POST http://localhost:9010/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "x-rh-identity: <base64-encoded-identity>" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": 2,
    "params": {}
  }'

# Call whoami tool
curl -X POST http://localhost:9010/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "x-rh-identity: <base64-encoded-identity>" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "id": 3,
    "params": {
      "name": "whoami",
      "arguments": {}
    }
  }'
```

## Architecture

### Components

```text
┌─────────────────────────────────────────────┐
│  HCC Gateway                                 │
│  - OIDC token validation                    │
│  - Injects x-rh-identity header             │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  MCP Server (this module)                   │
│  ┌─────────────────────────────────────┐   │
│  │ McpAuthMechanism                     │   │
│  │ - Validates x-rh-identity header     │   │
│  │ - Rejects unauthenticated requests   │   │
│  └──────────────┬──────────────────────┘   │
│                 ▼                            │
│  ┌─────────────────────────────────────┐   │
│  │ McpIdentityProvider                  │   │
│  │ - Parses x-rh-identity JSON          │   │
│  │ - Validates org_id, user_id, username│   │
│  │ - Creates McpPrincipal               │   │
│  └──────────────┬──────────────────────┘   │
│                 ▼                            │
│  ┌─────────────────────────────────────┐   │
│  │ MCP Tools (tools/*.java)             │   │
│  │ - Inject SecurityIdentity            │   │
│  │ - Access user's org_id, user_id      │   │
│  │ - Execute under user's permissions   │   │
│  │ - Use McpToolUtils for common logic  │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### Classes

#### Authentication Layer
- **`McpAuthMechanism`** - HTTP authentication mechanism, validates header presence
- **`McpAuthenticationRequest`** - Request object carrying x-rh-identity value
- **`McpIdentityProvider`** - Parses and validates x-rh-identity header
- **`RhIdentity`** - Record holding parsed identity fields (type, orgId, accountId, userId, username)
- **`McpPrincipal`** - Security principal wrapping RhIdentity

#### Tool Layer (Modular Structure)
- **`McpToolUtils`** - Shared utilities (parseUuid, parseDate, executeRestCall, httpErrorMessage)
- **`tools/ServerTools`** - Server information tools (serverInfo, whoami)
- **`tools/MetadataTools`** - Metadata retrieval (getSeverities, getBundle, getApplication, getEventType)
- **`tools/IntegrationTools`** - Integration management  
  - GET: getIntegrations, getIntegration, getIntegrationHistory, getIntegrationHistoryDetails  
  - Write: enableIntegration, disableIntegration, testIntegration, deleteIntegration, createIntegration, updateIntegration
- **`tools/EventTools`** - Event log queries (getEvents)
- **`tools/UserConfigTools`** - User preferences  
  - GET: getUserNotificationPreferences, getUserNotificationPreferencesByApplication  
  - Write: saveUserNotificationPreferences
- **`tools/OrgConfigTools`** - Organization configuration  
  - GET: getDailyDigestTimePreference  
  - Write: setDailyDigestTimePreference

## Configuration

Authentication is enforced programmatically by `McpAuthMechanism`, not via `quarkus.http.auth.permission` properties. Health (`/q/health`) and metrics (`/q/metrics`) paths are exempt — see `McpAuthMechanism.ANONYMOUS_PATHS`.

## Development

### Adding New MCP Tools

The codebase follows a modular structure mirroring `backend/routers/handlers`. Each `*Tools.java` class in the `tools` package contains tools for a specific functional area.

**Example: Adding a read-only tool**
```java
@ApplicationScoped
public class MetadataTools {
    @Inject SecurityIdentity securityIdentity;
    @Inject @RestClient BackendRestClient backendClient;
    @Inject MeterRegistry registry;

    @Tool(description = "My new tool")
    public String myTool(@ToolArg(description = "Parameter") String param) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("myTool", principal,
                () -> backendClient.myEndpoint(principal.getRawHeader(), param), registry);
    }
}
```

**Example: Adding a write operation**
```java
@Tool(description = "Updates a resource")
public String updateResource(@ToolArg(description = "Resource ID") String id) {
    McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
    return McpToolUtils.executeRestCall("updateResource", principal,
            () -> backendClient.updateResource(principal.getRawHeader(), 
                    McpToolUtils.parseUuid("id", id)), registry);
}
```

**Shared utilities available in `McpToolUtils`:**
- `parseUuid(paramName, value)` - Parse and validate UUID parameters
- `parseDate(paramName, value)` - Parse yyyy-MM-dd date strings to LocalDate
- `executeRestCall(toolName, principal, restCall, registry)` - Execute REST calls with error handling and metrics
- `httpErrorMessage(status)` - User-friendly error messages for HTTP status codes

## References

- [ADR-084](https://github.com/openshift-online/architecture/pull/67) - MCP Authentication in HCC
- [Model Context Protocol Specification](https://spec.modelcontextprotocol.io/)
- [Quarkus MCP Server Extension](https://github.com/quarkiverse/quarkus-mcp-server)
