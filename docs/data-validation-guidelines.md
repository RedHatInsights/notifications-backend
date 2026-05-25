# Data Validation Guidelines

## Validation Architecture

- Place Bean Validation annotations (`@NotNull`, `@Size`, `@Pattern`, etc.) on **both** the DTO classes in `backend/src/main/java/.../models/dto/` **and** the JPA entity classes in `common/src/main/java/.../models/`. The DTO layer validates API input; the entity layer enforces persistence invariants.
- Trigger validation at the **endpoint layer** by annotating resource method parameters with `@NotNull @Valid`. Prefer `@NotNull @Valid @RequestBody` for request bodies and `@BeanParam @Valid` for query parameter objects like `Query`.
- Avoid calling `Validator.validate()` manually in service or repository code. Rely on the JAX-RS integration to trigger validation automatically from resource methods.

## Bean Validation Conventions

- Use `@NotNull` for required fields. Use `@NotBlank` only for String fields where whitespace-only values must also be rejected (e.g., `displayName` on `CreateBehaviorGroupRequest`, `message` on `EndpointTestRequest`).
- Annotate String length limits with `@Size(max = N)` and include a custom `message` when the default is unclear:
  ```java
  @Size(max = 150, message = "the display name cannot exceed {max} characters")
  ```
- Use `@Pattern(regexp = "[a-z][a-z_0-9-]*")` for machine-readable name fields (application names, event type names). This pattern is the repo standard -- lowercase, starting with a letter, allowing underscores and hyphens.
- Use `@Min` / `@Max` for numeric bounds. See `Query` for the pagination pattern: `@Min(1)` on `pageSize`, `@Max(200)` on `pageSize`, `@Min(0)` on `offset`.

## Cross-Field Validation

- Implement cross-field validation as private boolean methods annotated with `@AssertTrue` or `@AssertFalse` and `@JsonIgnore`. Return `true` from `@AssertTrue` methods when the state is valid:
  ```java
  @JsonIgnore
  @AssertTrue(message = "either the bundle name or the bundle UUID are required")
  private boolean isBundleUuidOrBundleNameValid() {
      return this.bundleId != null || (this.bundleName != null && !this.bundleName.isBlank());
  }
  ```
- Use `@AssertFalse` when the condition is more naturally expressed as "this bad state should not exist" (see `UpdateBehaviorGroupRequest.isDisplayNameNotNullAndBlank()`).

## Custom Validators

- The repo has one custom constraint annotation: `@ValidNonPrivateUrl` in `common/src/main/java/.../models/validation/`. Apply it to any user-supplied URL field on both the DTO and entity (e.g., `WebhookPropertiesDTO.url`, `CamelPropertiesDTO.url`).
- `ValidNonPrivateUrlValidator` enforces: well-formed URL/URI, `http` or `https` scheme only, no private IPs (site-local), no loopback addresses (except in dev/test mode via `LaunchMode`).
- Place new custom constraint annotations in the `common` module under `com.redhat.cloud.notifications.models.validation` so they are available to both `backend` and `common`.
- Write a corresponding unit test using `Validation.buildDefaultValidatorFactory()` in `common/src/test/` (see `ValidNonPrivateUrlValidatorTest` for the pattern).

## JSON Serialization

- Annotate all DTOs, request models, and API-facing entities with `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)`. This is the repo-wide API contract for JSON field names.
- `RegisterCustomModuleCustomizer` enables `ACCEPT_CASE_INSENSITIVE_ENUMS` globally. Do not add per-field enum deserialization workarounds.
- Use `@JsonIgnoreProperties(ignoreUnknown = true)` on request models that may receive extra fields from clients (e.g., `CreateBehaviorGroupRequest`, `UpdateBehaviorGroupRequest`, `SettingsValuesByEventType`).
- Use `@JsonProperty(access = READ_ONLY)` for server-generated fields like `id`, `created`, `updated` to prevent clients from setting them.
- Use `@JsonInclude(JsonInclude.Include.NON_NULL)` on optional response fields, not at class level.

## ApiResponseFilter

- When a JPA entity is serialized directly as an API response and certain fields need to be conditionally excluded, annotate the entity class with `@JsonFilter(ApiResponseFilter.NAME)` and add the filtering logic to `ApiResponseFilter`.
- Currently used on: `EventType`, `BehaviorGroup`, `InstantEmailTemplate`, `AggregationEmailTemplate`. Add boolean `filterOut*` fields to control serialization dynamically per endpoint.
- Register all `@JsonFilter`-annotated classes in `ApiObjectMapperCustomizer` via the `SimpleFilterProvider`.

## DTO / Mapper Layer

- Use MapStruct (`@Mapper(componentModel = MappingConstants.ComponentModel.CDI)`) for entity-to-DTO mapping. See `EndpointMapper` and `CommonMapper`.
- On mapper methods going from DTO to entity, explicitly `@Mapping(target = "...", ignore = true)` for all server-managed fields (`accountId`, `orgId`, `updated`, etc.) to prevent client injection.
- Polymorphic endpoint properties use `@JsonSubTypes` on `EndpointDTO.properties` with `@JsonTypeInfo(use = Id.NAME, property = "type", include = As.EXTERNAL_PROPERTY)` to discriminate by endpoint type.

## Error Response Formats

- `ConstraintViolationExceptionMapper` returns HTTP 400 with a JSON body:
  ```json
  {"title": "Constraint Violation", "description": "The submitted payload is incorrect", "violations": [{"field": "...", "message": "..."}]}
  ```
  Do not change this format -- the admin-console UI depends on it.
- `JaxRsExceptionMapper` converts `BadRequestException` and `JsonParseException` (wrapped by Quarkus) to HTTP 400 with the exception message as the body.
- For programmatic validation failures in resource methods, throw `BadRequestException` with a descriptive message. Do not return `Response.status(BAD_REQUEST)` unless building a structured error body (see `ValidationResource`).

## Sort Field Validation

- `Sort.getSort()` validates `sort_by` query parameters against a whitelist `Map<String, String>` of allowed sort fields (e.g., `Endpoint.SORT_FIELDS`). The map keys must be lowercase.
- The sort value is validated against pattern `^[a-z0-9_-]+(:(asc|desc))?$`. Unknown sort fields throw `BadRequestException`.

## Internal Validation Endpoint

- `/api/internal/validation/baet` validates bundle/application/event-type triplets.
- `/api/internal/validation/message` and `/api/internal/validation/console-cloud-event` validate incoming message payloads using `Parser.validate()` and `ConsoleCloudEventParser.validate()` respectively. Errors return `MessageValidationResponse` with a `Map<String, List<String>>` of field-to-error-messages.

## Verification

```bash
# Compile and check for Bean Validation annotation issues
./mvnw compile -pl backend,common -q

# Run custom validator tests
./mvnw test -pl common -Dtest=ValidNonPrivateUrlValidatorTest -q

# Run behavior group request validation tests
./mvnw test -pl backend -Dtest=CreateBehaviorGroupRequestTest,UpdateBehaviorGroupRequestTest -q

# Run checkstyle
./mvnw checkstyle:check -q

# Run endpoint resource tests (includes validation error path coverage)
./mvnw test -pl backend -Dtest=EndpointResourceTest -q
```
