# v2 subscriptions API — design contract (RHCLOUD-49569)

Spike for the Lightwell onboarding epic (RHCLOUD-49566). Defines the contract for two new
v2 endpoints — GET and PUT — that let a client read and bulk-update a user's notification
subscriptions across bundles, applications, event types, and subscription channels.

This is deliberately designed to be as close as possible to the future v3 preferences API,
which is planned to fully replace the current v1 `UserConfigResource`
(`/api/notifications/v1.0/user-config/notification-event-type-preference`). Lightwell is the
first consumer, but nothing in this contract is Lightwell-specific — it is a general-purpose
subscriptions API.

## Context

Lightwell is a new, top-priority product integrating with the Notifications service. Unlike
existing tenants, it wants its own subscription UI rather than the standard HCC "User
Preferences" page. To support that without forking the whole preferences system, this API
gives Lightwell (and future consumers) a way to read and write subscription state
programmatically.

Lightwell's own UI shows a single, cumulative severity threshold ("Important and above")
rather than our discrete severity list. Expanding that threshold into an explicit list of
severities is entirely Lightwell's responsibility — this contract only deals in explicit
severities, never thresholds. Similarly, "repository" is a Lightwell concept with no
equivalent here: each Lightwell repository maps to its own dedicated event type, and that
mapping is owned entirely by Lightwell (see RHCLOUD-49566 for details).

## Endpoints

```
GET  /api/notifications/v2.0/user-config/subscriptions
PUT  /api/notifications/v2.0/user-config/subscriptions
```

Resource: `UserConfigResourceV2`, following the existing `X`/`XV2` versioning split used
elsewhere in the codebase (`EndpointResource`/`EndpointResourceV2`,
`NotificationResource`/`NotificationResourceV2`). Both endpoints act on the authenticated
user's own subscriptions (`orgId`/`userId` from the security context) — there is no
account/admin-level variant.

## Shape: a tree, not a flat list

The response (and request) is a tree: bundle → applications → event types → subscription
channels → severities. This mirrors the real data hierarchy and its uniqueness constraints:

- `bundle.name` is globally unique
- `application.name` is unique **within** a bundle (`UNIQUE(name, bundle_id)`)
- `event_type.name` is unique **within** an application (`UNIQUE(name, application_id)`)
- `severity` is not a hierarchy level with its own identity — it's the `Severity` enum
  (`CRITICAL`/`IMPORTANT`/`MODERATE`/`LOW`/`NONE`), an attribute of an event type
  (`available_severities`) and of a subscription (`subscribed_severities`)
- subscription channel (`INSTANT`/`DAILY`/`DRAWER`, the existing `SubscriptionType` enum)
  sits between event type and severities, because a user can subscribe to distinct severities
  per channel (e.g. only `CRITICAL` daily, but `CRITICAL`+`IMPORTANT` instantly)

`bundle`/`application`/`event_type` are always the technical, lowercase names (constrained by
`@Pattern("[a-z][a-z_0-9-]*")` on all three entities) — not display names. Display names are
separate, read-only fields (`bundle_display_name`, `application_display_name`, event type
`display_name`).

## Serialization: subscription channel and severity casing differ from the Java enums

This API does not serialize `SubscriptionType`/`Severity` using their Java constant names —
both are given a v2-specific JSON representation:

- Subscription channel (`SubscriptionType`): `INSTANT` → `instant_email`, `DAILY` →
  `daily_email`, `DRAWER` → `drawer`.
- Severity (`Severity`): the lowercased constant name — `CRITICAL` → `critical`, `IMPORTANT` →
  `important`, `MODERATE` → `moderate`, `LOW` → `low`, `NONE` → `none`. `UNDEFINED` is never
  surfaced by this API.

`SubscriptionType` and `Severity` are shared domain enums used well beyond this API (Kafka
payloads, v1 endpoints, templates), so their Java constant names and `Severity`'s existing
`@JsonProperty("CRITICAL")`-style values must not change to accommodate this one API.

Instead, follow the existing precedent set by `PagerDutySeverityDTO`
(`backend/.../models/dto/v1/endpoint/properties/PagerDutySeverityDTO.java`), which mirrors a
domain enum (`PagerDutySeverity`) one-for-one under a constant name but carries its own
lowercase `@JsonProperty` values:

- Add `SubscriptionTypeDTO` and `SeverityDTO` under a new `models/dto/v2` package. Each DTO enum
  keeps the same constant names as its domain counterpart (`SeverityDTO` omits `UNDEFINED`) and
  annotates each constant with the new `@JsonProperty` value shown above.
- Map DTO ↔ domain enum with a MapStruct method on `SubscriptionMapper`. For `SubscriptionType`
  (same 3 constants both sides) this is a plain method with no `@Mapping`/`@ValueMapping` —
  MapStruct matches by constant name automatically, the same mechanism `EndpointMapper.pagerDutyToDTO`
  relies on for `PagerDutySeverity` ↔ `PagerDutySeverityDTO`. `Severity` currently has one constant
  (`UNDEFINED`) with no `SeverityDTO` counterpart, which MapStruct must be told how to handle or it
  won't compile — rather than naming `UNDEFINED` explicitly (it's slated for removal from `Severity`
  separately, and that removal shouldn't require touching this mapper), `severityToSeverityDTO` uses
  `@ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)`.
  `ANY_REMAINING` (not `ANY_UNMAPPED`) matters here: it keeps MapStruct's normal by-name matching for
  every constant that does have a `SeverityDTO` counterpart, and only routes the ones that don't
  (currently just `UNDEFINED`) to the given target — `ANY_UNMAPPED` would skip by-name matching
  entirely and route everything through the catch-all, which is not what's wanted.
- The resource layer builds/reads the tree entirely in terms of the DTO enums; the mapper
  converts to/from the domain enums right at the boundary with `EventTypeRepository`/
  `SubscriptionRepository`, which still take the domain `SubscriptionType`/`Severity`.

## GET

```
GET /api/notifications/v2.0/user-config/subscriptions
GET /api/notifications/v2.0/user-config/subscriptions?bundle=lightwell
GET /api/notifications/v2.0/user-config/subscriptions?bundle=lightwell&application=lightwell
GET /api/notifications/v2.0/user-config/subscriptions?bundle=lightwell&application=lightwell&event_type=java-remediated
```

Optional query params, each narrowing the returned tree: `bundle`, `application` (requires
`bundle`), `event_type` (requires `bundle` and `application` — event type names aren't
globally unique, only within their application). `application` without `bundle`, or
`event_type` without both, is a `400`. No params returns the full tree for everything the
authenticated user can see — this is the same "fetch everything in one call" shape the
existing v1 preferences page already relies on today, so it isn't a new scalability concern.

Response `200`:

```json
[
  {
    "bundle": "lightwell",
    "bundle_display_name": "Lightwell",
    "applications": [
      {
        "application": "lightwell",
        "application_display_name": "Lightwell",
        "event_types": [
          {
            "event_type": "java-remediated",
            "display_name": "Java Remediated",
            "available_severities": ["critical", "important", "moderate", "low", "none"],
            "subscriptions": [
              { "subscription_type": "instant_email", "subscribed_severities": ["critical", "important"] },
              { "subscription_type": "daily_email", "subscribed_severities": ["critical"] },
              { "subscription_type": "drawer", "subscribed_severities": [] }
            ]
          },
          {
            "event_type": "python-remediated",
            "display_name": "Python Remediated",
            "available_severities": ["critical", "important"],
            "subscriptions": [
              { "subscription_type": "instant_email", "subscribed_severities": [] },
              { "subscription_type": "daily_email", "subscribed_severities": [] },
              { "subscription_type": "drawer", "subscribed_severities": [] }
            ]
          }
        ]
      }
    ]
  }
]
```

Field semantics:

- `available_severities` — the severities this event type supports (`EventType.availableSeverities`).
  Shared across all subscription channels for that event type; there is no per-channel variant
  of this field, because the underlying column isn't per-channel either.
- `subscribed_severities` — the severities the authenticated user currently receives for that
  event type on that channel. An **empty list means fully unsubscribed** from that channel for
  that event type — there is no separate `enabled`/`subscribed` boolean. `NONE` is itself a
  valid severity value, so an empty list is unambiguous: it can only mean "nothing selected,"
  never "the NONE severity selected."
- When the user has no stored subscription row yet for a given event type/channel, the value
  falls back to the existing default logic (`EventType.subscribedByDefault`/`defaultSeverity`),
  same as v1 does today.

Note the `python-remediated` example above: its `available_severities` differs from
`java-remediated`'s. A consumer that assumes all event types under one application share the
same severity set (as Lightwell's UI currently does, rendering a single global severity
picker) should actually verify this rather than assume it — this contract doesn't guarantee
uniformity across event types.

## PUT

```
PUT /api/notifications/v2.0/user-config/subscriptions
```

Same tree shape as the GET response, but write-only: no `*_display_name` or
`available_severities` fields (those are read-only/derived, not settable).

```json
[
  {
    "bundle": "lightwell",
    "applications": [
      {
        "application": "lightwell",
        "event_types": [
          {
            "event_type": "java-remediated",
            "subscriptions": [
              { "subscription_type": "instant_email", "subscribed_severities": ["critical", "important"] },
              { "subscription_type": "daily_email", "subscribed_severities": ["critical"] }
            ]
          }
        ]
      }
    ]
  }
]
```

Semantics:

- **Partial update, not full replace.** Anything omitted from the tree — a bundle, an
  application, an event type, or a subscription channel — is left untouched, not reset or
  unsubscribed. In the example above, `drawer` is omitted for `java-remediated`: its current
  state (whatever it was) is unaffected. This is a deliberate choice: it lets a caller update
  a handful of event types without first fetching and re-sending the entire tree.
- **This is still "bulk, one call"** even though it's nested rather than a flat array — one
  PUT call can update any number of bundles/applications/event types/channels at once. The
  ticket's wording ("accepts a list of per-event-type settings in one call") is satisfied by
  the tree containing many event-type entries in one request, not by the top-level JSON value
  being a flat array. This is a deliberate, documented deviation from the literal phrasing in
  the ticket and worth flagging to reviewers.
- `subscribed_severities` must be a subset of that event type's `available_severities` — `400`
  per invalid entry, with enough detail (bundle/application/event_type/subscription_type) to
  identify which entry failed, since this is a bulk call.
- Any `bundle`/`application`/`event_type` name in the request that doesn't resolve to a real,
  existing entity is a `400` (not a partial-success/`207` — an invalid identifier is a client
  error the caller should fix and retry in full).
- Response: `204`.

## Persistence

No new schema or storage needed — this maps directly onto existing machinery:

- Event type resolution: `ApplicationRepository.getEventType(bundle, application, eventType)`
  (`backend/.../db/repositories/ApplicationRepository.java`) — the same triplet lookup this
  resource's module already has available (`backend` has no dependency on `engine`, so engine's
  own `EventTypeRepository.getEventType` isn't reachable from here; `ApplicationRepository`'s
  version does the same JPQL join).
- Write: `SubscriptionRepository.updateSubscription(orgId, userId, eventTypeId,
  subscriptionType, subscribed, severities)` (`backend/.../db/repositories/SubscriptionRepository.java`),
  called once per `(event_type, subscription_type)` leaf in the request tree, inside a single
  `@Transactional` method. `subscribed` is derived server-side as
  `!subscribedSeverities.isEmpty()` — the API itself has no `enabled`/`subscribed` field to pass
  through. `SubscriptionTypeDTO`/`SeverityDTO` values from the request are mapped to the domain
  `SubscriptionType`/`Severity` enums before this call (see "Serialization" above).
- Read: the existing `EventTypeEmailSubscription`/`SubscriptionRepository` read paths, grouped
  and nested into the tree shape at the resource layer.

## Deferred to v3

Kept out of this contract deliberately, to avoid over-building a v2 spike beyond what
Lightwell (or any near-term consumer) actually needs:

- Bulk cross-cutting writes to fields other than `subscribed_severities` (e.g. muting an
  entire bundle in one leaf) — not needed yet, and would expand the validation surface
  significantly.
- Any change to how `available_severities` is computed or stored — this contract only reads
  the existing per-event-type value.

## Authorization

Same pattern as `OrgConfigResource`/`UserConfigResource`: `@Authorization` with
`RBAC_READ_NOTIFICATIONS`/`NOTIFICATIONS_VIEW` on GET, `RBAC_WRITE_NOTIFICATIONS`/
`NOTIFICATIONS_EDIT` on PUT. This is a self-service, per-user API — there's no separate
admin/account-level authorization tier.

## Open item unrelated to this contract

`common-template/src/main/resources/templates/drawer/Advisor/aaa.md` is a draft epic/story
planning doc for the whole Lightwell onboarding effort, committed under a template resource
path rather than `docs/`. It should be moved (or turned into the real Jira epic/stories it
describes) separately from this design doc.
