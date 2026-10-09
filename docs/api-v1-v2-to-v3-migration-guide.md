# Notifications API V3 Migration Guide

This guide covers all breaking changes and new features when migrating from the V1/V2 APIs to V3, for services consuming the Notifications and Integrations APIs.

## OpenAPI Specifications

The full OpenAPI specs for each version are available on stage:

**Notifications API:**
- V1: https://console.stage.redhat.com/api/notifications/v1.0/openapi.json
- V2: https://console.stage.redhat.com/api/notifications/v2.0/openapi.json
- V3: https://console.stage.redhat.com/api/notifications/v3.0/openapi.json

**Integrations API:**
- V1: https://console.stage.redhat.com/api/integrations/v1.0/openapi.json
- V2: https://console.stage.redhat.com/api/integrations/v2.0/openapi.json
- V3: https://console.stage.redhat.com/api/integrations/v3.0/openapi.json

## Base Path Changes

| API              | V1                           | V2                           | V3                           |
|------------------|------------------------------|------------------------------|------------------------------|
| Integrations     | `/api/integrations/v1.0`     | `/api/integrations/v2.0`     | `/api/integrations/v3.0`     |
| Notifications    | `/api/notifications/v1.0`    | `/api/notifications/v2.0`    | `/api/notifications/v3.0`    |

---

## Integrations API (`/api/integrations/v3.0/endpoints`)

### Secrets Management (Breaking Change)

The most significant change in V3 is how secrets (secret tokens and bearer authentication) are handled. In V1/V2, secrets were embedded in the endpoint properties and returned (redacted) in responses. **V3 never returns secrets in any response.**

#### Creating an Endpoint with Secrets

In V1, secrets were set directly in the `properties` object:

```json
// V1 — POST /api/integrations/v1.0/endpoints
{
  "name": "My Webhook",
  "type": "webhook",
  "properties": {
    "url": "https://example.com/hook",
    "method": "POST",
    "secret_token": "my-secret",
    "bearer_authentication": "my-bearer-token",
    "disable_ssl_verification": false
  }
}
```

In V3, secrets are provided via an optional top-level `secrets` field at creation time. The `secrets` field is write-only and never included in responses:

```json
// V3 — POST /api/integrations/v3.0/endpoints
{
  "name": "My Webhook",
  "type": "webhook",
  "properties": {
    "url": "https://example.com/hook"
  },
  "secrets": {
    "secret_token": "my-secret",
    "bearer_authentication": "my-bearer-token"
  }
}
```

#### Updating Secrets on an Existing Endpoint

In V1, secrets were updated inline when updating the endpoint via `PUT /endpoints/{id}`.

In V3, the `PUT /endpoints/{id}` endpoint **ignores any secrets in the payload**. Use the dedicated secrets endpoints instead:

| Method   | Path                        | Description                     |
|----------|-----------------------------|---------------------------------|
| `PUT`    | `/endpoints/{id}/secrets`   | Create or replace secrets       |
| `DELETE` | `/endpoints/{id}/secrets`   | Delete all secrets              |

**`PUT /endpoints/{id}/secrets`** request body:

```json
{
  "secret_token": "new-secret",
  "bearer_authentication": "new-bearer-token"
}
```

Omitting a field clears that particular secret. To clear all secrets at once, use `DELETE /endpoints/{id}/secrets`.

### Webhook Properties Changes

| Field                      | V1       | V3                               |
|----------------------------|----------|----------------------------------|
| `url`                      | Present  | Present (unchanged)              |
| `method`                   | Required | **Removed** (hardcoded to POST)  |
| `disable_ssl_verification` | Required | **Removed**                      |
| `secret_token`             | Optional | **Removed** (use `secrets`)      |
| `bearer_authentication`    | Optional | **Removed** (use `secrets`)      |

**Migration:** Remove `method`, `disable_ssl_verification`, `secret_token`, and `bearer_authentication` from the `properties` object. Only `url` remains. Pass secrets via the top-level `secrets` field on create, or `PUT /endpoints/{id}/secrets` for updates.

### Camel Properties Changes (Slack, Google Chat, Teams, ServiceNow, Splunk)

| Field                      | V1       | V3                               |
|----------------------------|----------|----------------------------------|
| `url`                      | Present  | Present (unchanged)              |
| `extras`                   | Optional | Optional (unchanged)             |
| `disable_ssl_verification` | Required | **Removed**                      |
| `secret_token`             | Optional | **Removed** (use `secrets`)      |

### PagerDuty Properties Changes

| Field          | V1                   | V3                               |
|----------------|----------------------|----------------------------------|
| `severity`     | Required             | **Removed**                      |
| `secret_token` | Required             | **Removed** (use `secrets`)      |

**Migration:** The PagerDuty properties object is now empty. The integration key (previously `secret_token`) must be provided via the `secrets` field on create, or `PUT /endpoints/{id}/secrets` for updates.

### System Subscription Properties Changes

| Field                | V1       | V3                            |
|----------------------|----------|-------------------------------|
| `only_admins`        | Present  | Present (unchanged)           |
| `group_ids`          | Present  | Present (unchanged)           |
| `group_id`           | Present  | **Removed** (use `group_ids`) |
| `ignore_preferences` | Present  | **Removed**                   |

### Endpoint Object Field-Level Changes

| Field                                           | V1                  | V3                                |
|-------------------------------------------------|---------------------|-----------------------------------|
| `status`                                        | Read-write          | **Read-only**                     |
| `event_types`                                   | Read-write          | **Write-only**                    |
| `event_types_group_by_bundles_and_applications` | Read-write          | **Read-only**                     |
| `secrets`                                       | Not present         | **New, write-only** (create only) |

### Endpoint Listing Response Format

V1 returns a page object without pagination links. V3 returns a page with full pagination links:

```json
// V3 response — GET /api/integrations/v3.0/endpoints
{
  "data": [ /* endpoint objects */ ],
  "links": {
    "first": "/api/integrations/v3.0/endpoints?limit=20&offset=0",
    "last":  "/api/integrations/v3.0/endpoints?limit=20&offset=80",
    "prev":  null,
    "next":  "/api/integrations/v3.0/endpoints?limit=20&offset=20"
  },
  "meta": {
    "count": 95
  }
}
```

### Endpoint History Response

V1 returns a bare array of notification history entries.
V3 (and V2) return a paginated response:

```json
// V3 response — GET /api/integrations/v3.0/endpoints/{id}/history
{
  "data": [ /* notification history entries */ ],
  "links": { "first": "...", "last": "...", "prev": null, "next": "..." },
  "meta": { "count": 42 }
}
```

### Removed Endpoints (Integrations API)

The following V1 endpoints are not available in V3:

| V1 Endpoint                                          | V3 Alternative                                                                |
|------------------------------------------------------|-------------------------------------------------------------------------------|
| `POST /endpoints/system/email_subscription`          | None (system endpoints are managed as regular endpoints)                      |
| `POST /endpoints/system/drawer_subscription`         | None (system endpoints are managed as regular endpoints)                      |
| `PUT /endpoints/{id}/eventType/{eventTypeId}`        | Use the `event_types` field on `POST /endpoints` or `PUT /endpoints/{id}`     |
| `DELETE /endpoints/{id}/eventType/{eventTypeId}`     | Use the `event_types` field on `PUT /endpoints/{id}`                          |
| `PUT /endpoints/{id}/eventTypes`                     | Use the `event_types` field on `PUT /endpoints/{id}`                          |

### New Endpoints (Integrations API)

| Method   | Path                        | Description                                    |
|----------|-----------------------------|------------------------------------------------|
| `PUT`    | `/endpoints/{id}/secrets`   | Create or update an endpoint's secrets         |
| `DELETE` | `/endpoints/{id}/secrets`   | Delete all secrets associated with an endpoint |

---

## Notifications API (`/api/notifications/v3.0/notifications`)

### Response Schema Changes

V1 returns raw internal entities in several endpoints. V3 returns cleaner, purpose-built response schemas with a more stable contract.

| Endpoint                                                                  | Change Summary                                    |
|---------------------------------------------------------------------------|---------------------------------------------------|
| `GET /eventTypes`                                                         | New response schema                               |
| `GET /bundles/{bundleName}`                                               | New dedicated response schema                     |
| `GET /bundles/{bundleName}/applications/{appName}`                        | New dedicated response schema                     |
| `GET /bundles/{bundleName}/applications/{appName}/eventTypes/{etName}`    | New response schema                               |
| `GET /eventTypes/{id}/endpoints`                                          | Uses V3 endpoint schema (no secrets)              |

### Field Changes in V3 Response Schemas

**Application** changes:
- `id` is read-only
- `bundle_id` field is present (V1 returned a nested `bundle` object or omitted the relationship)

**Bundle** changes:
- `id` is read-only
- `applications` is an ordered list (V1 returned a set with no guaranteed order)

### New Endpoints (Notifications API)

| Method | Path                | Description                                 | V1 Equivalent                            |
|--------|---------------------|---------------------------------------------|------------------------------------------|
| `GET`  | `/applications`     | List applications (full schema)             | `GET /facets/applications` (Facet only)  |
| `GET`  | `/bundles`          | List bundles (full schema, optional apps)   | `GET /facets/bundles` (Facet only)       |

The new `/applications` and `/bundles` endpoints return full objects with all fields instead of the simplified `Facet` format (`{ id, name, display_name }`) used in V1.

`GET /bundles` supports an `includeApplications` query parameter (boolean, default `false`) to include each bundle's applications in the response.

### Removed Endpoints (Notifications API)

All behavior group management endpoints have been removed from V3. Event type-to-endpoint linking is managed directly:

| V1 Endpoint                                                         | V3 Status   |
|---------------------------------------------------------------------|-------------|
| `GET /eventTypes/{eventTypeId}/behaviorGroups`                     | **Removed** |
| `GET /eventTypes/affectedByRemovalOfBehaviorGroup/{id}`            | **Removed** |
| `GET /behaviorGroups/affectedByRemovalOfEndpoint/{id}`             | **Removed** |
| `POST /behaviorGroups`                                             | **Removed** |
| `PUT /behaviorGroups/{id}`                                         | **Removed** |
| `DELETE /behaviorGroups/{id}`                                      | **Removed** |
| `PUT /behaviorGroups/{id}/actions`                                 | **Removed** |
| `PUT /eventTypes/{id}/behaviorGroups`                              | **Removed** |
| `PUT /eventTypes/{id}/behaviorGroups/{bgId}`                       | **Removed** |
| `DELETE /eventTypes/{id}/behaviorGroups/{bgId}`                    | **Removed** |
| `GET /bundles/{bundleId}/behaviorGroups`                           | **Removed** |
| `GET /facets/applications`                                         | **Replaced** by `GET /applications` |
| `GET /facets/bundles`                                              | **Replaced** by `GET /bundles`      |

---

## Events API (`/api/notifications/v3.0/notifications/events`)

### Date Parameter Changes (Breaking Change)

| Parameter   | V1 Name     | V1 Format                                                | V3 Name          | V3 Format                           |
|-------------|-------------|----------------------------------------------------------|------------------|--------------------------------------|
| Start date  | `startDate` | `yyyy-MM-dd` or `yyyy-MM-dd'T'HH:mm:ss` (auto-expanded) | `startDateTime`  | `yyyy-MM-dd'T'HH:mm:ss` only        |
| End date    | `endDate`   | `yyyy-MM-dd` or `yyyy-MM-dd'T'HH:mm:ss` (auto-expanded) | `endDateTime`    | `yyyy-MM-dd'T'HH:mm:ss` only        |

**Migration:** Rename `startDate` to `startDateTime` and `endDate` to `endDateTime`. If you were passing date-only values (`2024-01-15`), you must now pass full date-time values (`2024-01-15T00:00:00` for start, `2024-01-15T23:59:59` for end).

---

## User Config / Subscriptions API (`/api/notifications/v3.0/user-config`)

### Get Subscriptions

The V3 subscriptions endpoint returns a hierarchical tree of bundles, applications, and event types with their subscription channels:

```
GET /api/notifications/v3.0/user-config/subscriptions
```

Optional query parameters for narrowing the response:
- `bundle` — restrict to a specific bundle
- `application` — restrict to a specific application (requires `bundle`)
- `event_type` — restrict to a specific event type (requires `bundle` and `application`)

### Update Subscriptions

```
PUT /api/notifications/v3.0/user-config/subscriptions
```

V3 performs a **partial update** (not a full replace): any bundle, application, event type, or channel omitted from the request is left untouched. An empty array is accepted (unlike V2 which required a non-empty body).

### Notification Event Type Preferences (Private / Internal)

The following private (non-public-API) endpoints are now available at the V3 path, mirroring their V1 equivalents. These are used internally by the Hybrid Cloud Console UI for the legacy notification preferences form:

| Method | V1 Path                                                                                     | V3 Path                                                                                     |
|--------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `POST` | `/api/notifications/v1.0/user-config/notification-event-type-preference`                    | `/api/notifications/v3.0/user-config/notification-event-type-preference`                    |
| `GET`  | `/api/notifications/v1.0/user-config/notification-event-type-preference`                    | `/api/notifications/v3.0/user-config/notification-event-type-preference`                    |
| `GET`  | `/api/notifications/v1.0/user-config/notification-event-type-preference/{bundle}/{app}`     | `/api/notifications/v3.0/user-config/notification-event-type-preference/{bundle}/{app}`     |

These endpoints use the same request/response schemas as V1 (`SettingsValuesByEventType` for POST, `SettingsValueByEventTypeJsonForm` for GET). No behavioral changes — they delegate to the same shared implementation.

---

## Unchanged APIs

The following APIs are identical between V1 and V3 (same behavior, just exposed at the V3 path prefix):

- **Status** (`/api/notifications/v3.0/notifications/status`)
- **Org Config** (`/api/notifications/v3.0/notifications/org-config`)
- **Drawer** (`/api/notifications/v3.0/notifications/drawer`)

---

## Quick Migration Checklist

- [ ] Update all base paths from `v1.0` or `v2.0` to `v3.0`
- [ ] Remove `method`, `disable_ssl_verification` from webhook properties
- [ ] Remove `severity`, `secret_token` from PagerDuty properties
- [ ] Remove `disable_ssl_verification`, `secret_token` from Camel properties
- [ ] Move `secret_token` and `bearer_authentication` out of `properties` and into the top-level `secrets` field (create) or `PUT /endpoints/{id}/secrets` (update)
- [ ] Stop reading secrets from endpoint responses (they are no longer returned)
- [ ] Update secret management to use `PUT /endpoints/{id}/secrets` and `DELETE /endpoints/{id}/secrets`
- [ ] Stop writing to `status` on endpoints (now read-only)
- [ ] Treat `event_types` as write-only; use `event_types_group_by_bundles_and_applications` for reading linked event types
- [ ] Replace `GET /facets/applications` with `GET /applications`
- [ ] Replace `GET /facets/bundles` with `GET /bundles`
- [ ] Remove all behavior group API calls (create, update, delete, link/unlink) — manage event type-to-endpoint links directly
- [ ] Rename `startDate`/`endDate` to `startDateTime`/`endDateTime` in event log queries, and use `yyyy-MM-dd'T'HH:mm:ss` format
- [ ] Adapt to paginated response format for endpoint history (no longer a bare array)
- [ ] Adapt to paginated response with pagination links for endpoint listings
- [ ] Update response parsing for notification resources (EventType, Bundle, Application now use dedicated schemas)
- [ ] Use `group_ids` instead of `group_id` for system subscription properties
