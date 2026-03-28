# API Contracts Guidelines

Rules and conventions for REST API contracts in `notifications-backend`, derived from existing patterns. All agents MUST follow these during implementation and review.

## 1. API Versioning Strategy

- Two public API groups exist: `integrations` and `notifications`, each with v1.0 and v2.0.
- Base paths are constants in `com.redhat.cloud.notifications.Constants`:
  - `API_INTEGRATIONS_V_1_0 = "/api/integrations/v1.0"`
  - `API_INTEGRATIONS_V_2_0 = "/api/integrations/v2.0"`
  - `API_NOTIFICATIONS_V_1_0 = "/api/notifications/v1.0"`
  - `API_NOTIFICATIONS_V_2_0 = "/api/notifications/v2.0"`
  - `API_INTERNAL = "/internal"`
- Version binding uses a **static inner class pattern**. The resource class contains business logic; a static inner class (e.g., `V1`, `V2`) applies only the `@Path` annotation:
  ```java
  public class EndpointResource extends EndpointResourceCommon {
      @Path(API_INTEGRATIONS_V_1_0 + "/endpoints")
      static class V1 extends EndpointResource { }
  }
  ```
- When v2 extends behavior, create a separate `*V2` class (e.g., `EndpointResourceV2`) with its own inner class `V2`. Factor shared logic into a `*Common` base class (e.g., `EndpointResourceCommon`).
- V2 endpoints that overlap V1 should set an explicit `operationId` via `@Operation(operationId = "EndpointResource$V2_methodName")` to avoid OpenAPI ID collisions.
- The key V1-to-V2 difference is paginated responses: V1 returns raw `List<T>`, V2 returns `Page<T>` with links and meta.

## 2. OpenAPI / Swagger Annotations

- Use **MicroProfile OpenAPI** annotations (`org.eclipse.microprofile.openapi.annotations.*`), not Swagger annotations.
- Public endpoints should have `@Operation(summary = "...", description = "...")`. Deprecated endpoints may use `@Operation(hidden = true)`.
- Use `@APIResponse` / `@APIResponses` for non-default response codes (400, 404, 204).
- Pagination query params are documented via `@Parameters` / `@Parameter` blocks with `in = ParameterIn.QUERY` and `@Schema(type = SchemaType.INTEGER)`.
- Mark private/internal-only endpoints with `@Tag(name = OApiFilter.PRIVATE)`. These are filtered out of the public OpenAPI spec.
- Internal endpoints (under `/internal`) are excluded from scanning via `mp.openapi.scan.exclude.packages` in `application.properties`.
- The `OApiFilter` strips `DTO` suffix from schema names when no collision exists, so the public OpenAPI shows `Endpoint` instead of `EndpointDTO`.
- Operation IDs default to `CLASS_METHOD` strategy (`mp.openapi.extensions.smallrye.operationIdStrategy=CLASS_METHOD`).

## 3. DTO Layer and MapStruct Mapping

- DTOs live in the `backend` module under `com.redhat.cloud.notifications.models.dto.v1` (versioned package). Entity classes live in the `common` module under `com.redhat.cloud.notifications.models`.
- DTO classes are `final` classes (e.g., `EndpointDTO`), not interfaces or records.
- All DTO classes use `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` for snake_case JSON serialization.
- MapStruct mappers use `@Mapper(componentModel = MappingConstants.ComponentModel.CDI)` for CDI injection. They are declared as interfaces.
- Two mapper types exist:
  - `EndpointMapper` -- dedicated mapper for complex entities with polymorphic properties; uses `@Named` qualifiers and `default` methods with `switch` expressions for type dispatch.
  - `CommonMapper` -- general mapper for simple entity-to-DTO conversions (Bundle, Application, EventType, NotificationHistory).
- When mapping entity-to-DTO, use `@Mapping(target = "fieldName", ignore = true)` for fields not present in the target (e.g., `accountId`, `orgId`, internal IDs).
- Secrets/credentials mapped from Sources are excluded in DTO-to-entity direction: `@Mapping(target = "bearerAuthenticationSourcesId", ignore = true)`.
- Polymorphic properties (e.g., `EndpointPropertiesDTO`) use Jackson `@JsonSubTypes` and `@JsonTypeInfo(use = Id.NAME, property = "type", include = As.EXTERNAL_PROPERTY)` on the DTO field.

## 4. Request/Response Schema Conventions

- JSON field naming: **snake_case** everywhere via `@JsonNaming(SnakeCaseStrategy.class)` on DTOs and response models.
- Read-only fields use `@JsonProperty(access = JsonProperty.Access.READ_ONLY)` (e.g., `created`, `updated` timestamps).
- Nullable/optional fields use `@JsonInclude(JsonInclude.Include.NON_NULL)`.
- Timestamps use `LocalDateTime` with `@JsonFormat(shape = JsonFormat.Shape.STRING)`.
- Request body models for create/update are separate classes (e.g., `CreateBehaviorGroupRequest`, `UpdateBehaviorGroupRequest`), not the same as the response DTO.
- Response models for create operations can be separate (e.g., `CreateBehaviorGroupResponse`) with `@NotNull` on required output fields.
- Enum DTOs use `@JsonProperty("lowercase_value")` on each constant and `@Schema(enumeration = {...})` on the enum class.
- Request models use `@JsonIgnoreProperties(ignoreUnknown = true)` to tolerate extra fields.

## 5. Pagination

- Paginated list endpoints return `Page<T>` (`com.redhat.cloud.notifications.routers.models.Page` in `common` module) containing `data` (list), `links` (map of first/last/prev/next URLs), and `meta` (object with `count`).
- The `Query` class (`com.redhat.cloud.notifications.db.Query` in `backend` module) is a `@BeanParam` binding `limit`, `pageNumber`, `offset`, and `sort_by` query params.
- Pagination defaults: `limit` defaults to 20, max 200, min 1. `offset` takes precedence over `pageNumber`.
- `PageLinksBuilder.build(uriInfo, count, query)` constructs pagination links preserving all original query params.
- Some V1 endpoints (e.g., `EndpointResource.getEndpoints`) return `EndpointPage` (a typed `Page<EndpointDTO>` subclass). Prefer generic `Page<T>` for new V2 endpoints.
- Empty results return `Page.EMPTY_PAGE` or construct a Page with empty data, zero count, and computed links.

## 6. Content Type Handling

- Standard: `@Consumes(APPLICATION_JSON)` and `@Produces(APPLICATION_JSON)` using `jakarta.ws.rs.core.MediaType` constants.
- Some mutation endpoints produce `TEXT_PLAIN` for simple status responses (e.g., enable/disable returning `Response.ok()`).
- DELETE operations return `Response.noContent().build()` (204) with no body, using `@APIResponse(responseCode = "204")`.
- OpenAPI content types in `@APIResponse` use `@Content(mediaType = APPLICATION_JSON, schema = ...)`.

## 7. Path and Naming Conventions

- Resource paths use lowercase plural nouns: `/endpoints`, `/notifications`, `/behaviorGroups` (camelCase exception for behavior groups).
- Sub-resources use `/{id}/child`: `/endpoints/{id}/history`, `/eventTypes/{eventTypeId}/behaviorGroups`.
- Path parameters use UUID type: `@PathParam("id") UUID id` or `@RestPath UUID uuid`.
- Use `@RestPath` (RESTEasy Reactive) interchangeably with `@PathParam`. Both are used in the codebase.
- Use `@RestQuery` (RESTEasy Reactive) for query params as an alternative to `@QueryParam`.
- System/special endpoints use descriptive sub-paths: `/endpoints/system/email_subscription`.

## 8. Validation

- Request bodies: `@NotNull @Valid EndpointDTO endpointDTO`. The `@RequestBody` annotation is optional and used only for OpenAPI documentation.
- Bean validation on DTO fields: `@NotNull`, `@Size(max = 255)`, `@Min(0)`, `@NotBlank`.
- Custom cross-field validation uses `@JsonIgnore @AssertTrue(message = "...")` or `@AssertFalse(message = "...")` methods on DTOs (e.g., `isSubTypePresentWhenRequired()`, `isDisplayNameNotNullAndBlank()`).
- Custom validators: `@ValidNonPrivateUrl` on URL fields to reject private/internal URLs.
- `Query` params validated with `@Min` / `@Max` annotations directly on fields.
- `ConstraintViolationExceptionMapper` returns 400 with structured JSON: `{ "title", "description", "violations": [{ "field", "message" }] }`.
- Manual validation in handlers for cases Bean Validation cannot cover (e.g., RESTEasy not rejecting null UUIDs in `List<UUID>` bodies).

## 9. Authorization

- Most public endpoints should have `@Authorization(legacyRBACRole = RBAC_*, workspacePermissions = WorkspacePermission.*)`. Endpoints marked with `@Tag(name = OApiFilter.PRIVATE)` (e.g., UserConfigResource, DrawerResource, StatusResource) extract orgId/username from SecurityContext but do not use `@Authorization`.
- Read endpoints use `RBAC_READ_*` / `*_VIEW` permissions; write endpoints use `RBAC_WRITE_*` / `*_EDIT`.
- Internal endpoints use `@RolesAllowed(RBAC_INTERNAL_ADMIN)` or `@RolesAllowed(RBAC_INTERNAL_USER)` or `@PermitAll`.
- Credential redaction in responses is based on write permission: users without write access see `*****` for secrets.

## 10. Error Handling

- Throw JAX-RS exceptions directly: `BadRequestException`, `NotFoundException`, `ForbiddenException`.
- Exception mappers exist for: `ConstraintViolationException` (400), `JsonParseException`, `NotFoundException`, general `JaxRsExceptionMapper`.
- Error messages should be user-facing strings, not stack traces.
- Business rule violations use `BadRequestException` with descriptive messages (e.g., `"The endpoint URL must start with \"https\""`).
