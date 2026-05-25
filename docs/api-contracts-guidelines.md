# API Contracts Guidelines

## Path Structure and Versioning

- Public API paths follow the pattern `/api/{domain}/v{major}.0/{resource}` where domain is `notifications` or `integrations`. Use constants from `com.redhat.cloud.notifications.Constants` (e.g., `API_INTEGRATIONS_V_1_0`, `API_NOTIFICATIONS_V_2_0`).
- Internal API paths use `/internal` prefix (`API_INTERNAL` constant). Internal resources are annotated with `@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)` at the class level.
- The `IncomingRequestInterceptor` rewrites `/api/{domain}/v{N}/...` to `/api/{domain}/v{N}.0/...`, so clients can use either `v1` or `v1.0`.

## Resource Class Versioning Pattern

- V1 resources define a static inner class annotated with the versioned path that extends the resource:
  ```java
  public class EndpointResource extends EndpointResourceCommon {
      @Path(API_INTEGRATIONS_V_1_0 + "/endpoints")
      static class V1 extends EndpointResource { }
  }
  ```
- V2 resources follow the same pattern but extend a separate class. Most V2 GET endpoints rewrite to V1 via `IncomingRequestInterceptor` except for specific endpoints listed in the interceptor (e.g., `endpoints`, `eventTypes/{id}/behaviorGroups`).
- V2 endpoints that differ from V1 return `Page<T>` with pagination links and meta count. V1 endpoints return `List<T>` or domain-specific page types.
- When a V2 resource overrides a V1 endpoint, set an explicit `operationId` in `@Operation` using the format `"ResourceName$V2_methodName"`.

## Shared Logic Between Versions

- Extract shared logic into a `*Common` base class (e.g., `EndpointResourceCommon`) that both V1 and V2 resource classes extend.
- Use `protected` internal methods prefixed with `internal` for shared implementations (e.g., `internalGetEndpoints`, `internalGetEndpoint`).

## DTO Layer

- DTOs live in `models.dto.v1` package. Annotate all DTOs with `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` for snake_case JSON serialization.
- Use MapStruct mapper interfaces annotated with `@Mapper(componentModel = MappingConstants.ComponentModel.CDI)` for entity-to-DTO conversion. Place mappers alongside their DTOs (e.g., `EndpointMapper`, `CommonMapper`).
- Mapper interfaces use `@Named` qualified methods for polymorphic property mapping (e.g., mapping `EndpointProperties` subtypes to their respective DTO subtypes via a `default` switch method).
- Read-only fields use `@JsonProperty(access = JsonProperty.Access.READ_ONLY)`. Nullable fields use `@JsonInclude(JsonInclude.Include.NON_NULL)`.

## Pagination

- Paginated responses use `Page<T>` with `data` (list), `links` (map of first/last/prev/next URLs), and `meta` (count).
- Query pagination is handled via `@BeanParam @Valid Query query`. The `Query` class binds `limit` (default 20, max 200), `pageNumber`, `offset`, and `sort_by` from query parameters.
- Build pagination links with `PageLinksBuilder.build(uriInfo, count, query)` to preserve existing query parameters in link URLs.
- Domain-specific page types (e.g., `EndpointPage`) extend `Page<T>`.

## Authorization

- Annotate handler methods with `@Authorization(legacyRBACRole = ..., workspacePermissions = ...)`. This single annotation handles both legacy RBAC and Kessel permission checks depending on configuration.
- Read operations use `*_VIEW` permissions, write operations use `*_EDIT` permissions. For example: `RBAC_READ_INTEGRATIONS_ENDPOINTS` / `INTEGRATIONS_VIEW` for reads, `RBAC_WRITE_INTEGRATIONS_ENDPOINTS` / `INTEGRATIONS_EDIT` for writes.
- Obtain `orgId` and `accountId` from `SecurityContext` via `SecurityContextUtil.getOrgId(sec)` and `SecurityContextUtil.getAccountId(sec)`.

## Media Types

- Use `@Produces(APPLICATION_JSON)` for GET endpoints. Use both `@Consumes(APPLICATION_JSON)` and `@Produces(APPLICATION_JSON)` for POST/PUT endpoints that accept and return JSON.
- Some mutating endpoints return `@Produces(TEXT_PLAIN)` with `Response.ok().build()` or `Response.noContent().build()`.
- Import media type constants statically: `import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON`.

## Request Validation

- Annotate request body parameters with `@NotNull @Valid @RequestBody`. For required request bodies, add `@RequestBody(required = true)`.
- Use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`) on DTO fields and `@AssertTrue` for cross-field validation.
- Use `@Valid @BeanParam` for query parameter objects like `Query`.

## Error Handling

- Throw `BadRequestException` with a descriptive message string for client errors. Throw `NotFoundException` (with or without message) for missing resources.
- `JaxRsExceptionMapper` maps `WebApplicationException` subtypes to responses, returning the exception message as a plain-text entity.
- `ConstraintViolationExceptionMapper` returns a JSON body with `title`, `description`, and `violations` array (each with `field` and `message`).
- `NotFoundExceptionMapper` returns `TEXT_PLAIN` with the exception message.
- The global `ObjectMapper` has `ACCEPT_CASE_INSENSITIVE_ENUMS` enabled via `RegisterCustomModuleCustomizer`.

## OpenAPI Annotations

- Use MicroProfile OpenAPI annotations: `@Operation(summary, description)`, `@APIResponse`, `@APIResponses`, `@Parameter`, `@Tag`.
- Mark private/internal-only endpoints with `@Tag(name = OApiFilter.PRIVATE)` to exclude them from public OpenAPI docs.
- OpenAPI operation IDs default to `CLASS_METHOD` strategy (configured in `application.properties`).
- The `OApiFilter` class generates separate OpenAPI specs per domain (`/api/notifications/v1.0/openapi.json`, `/api/integrations/v1.0/openapi.json`) by filtering paths and schemas. DTO suffix removal happens automatically for schema names ending in `DTO` when no conflict exists.
- Internal and engine packages are excluded from OpenAPI scanning via `mp.openapi.scan.exclude.packages`.

## HTTP Method Conventions

- `DELETE` for resource deletion returns `Response.noContent().build()` (204).
- `PUT` for updates returns `Response.ok().build()` (200) with `TEXT_PLAIN`.
- `POST` for creation returns the created DTO directly (200), not 201.
- Enable/disable toggle endpoints use `PUT /{id}/enable` (enable, returns 200) and `DELETE /{id}/enable` (disable, returns 204).

## Maintenance Mode

- `MaintenanceModeRequestFilter` returns 503 with "Maintenance in progress" for affected paths. Internal, health, metrics, and status paths are excluded.

## Verification

```shell
# Compile and validate OpenAPI generation
./mvnw -pl backend quarkus:dev &
curl -s http://localhost:8085/api/integrations/v1.0/openapi.json | python3 -m json.tool > /dev/null
curl -s http://localhost:8085/api/notifications/v1.0/openapi.json | python3 -m json.tool > /dev/null

# Run backend tests (includes API contract tests)
./mvnw verify -pl backend

# Run checkstyle
./mvnw checkstyle:check -pl backend
```
