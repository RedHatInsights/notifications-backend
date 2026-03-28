# Database Guidelines

Rules and conventions for database usage in `notifications-backend`, derived from existing patterns. All agents MUST follow these during implementation and review.

## Flyway Migration Conventions

### File Naming
- Format: `V1.<sequence>.0__<JIRA-TICKET>_<description>.sql`
- Example: `V1.118.0__RHCLOUD-43290_event_deduplication.sql`
- The sequence number is monotonically increasing across the project (currently at 129)
- Description uses underscores as word separators
- Always include the Jira ticket ID (e.g., `RHCLOUD-43290`) after the double underscore

### Migration Location
- All migrations live in `database/src/main/resources/db/migration/`
- Flyway runs automatically at startup (`quarkus.flyway.migrate-at-start=true`)

### Migration Content Rules
- Use `snake_case` for all SQL identifiers (table names, column names, constraint names, index names)
- Name constraints explicitly with prefixes: `pk_` (primary key), `fk_` (foreign key), `ix_` (index)
- Example: `CONSTRAINT pk_event_deduplication PRIMARY KEY (...)`, `CONSTRAINT fk_event_deduplication_event_type_id FOREIGN KEY (...)`
- Index naming: `ix_<table>_<column(s)>` (e.g., `ix_event_deduplication_delete_after`)
- Add `COMMENT ON INDEX` when the purpose of an index is non-obvious
- Use `ON DELETE CASCADE` for foreign keys referencing parent entities
- Use `UUID` as the primary key type for all entity tables, generated via `public.gen_random_uuid()` in SQL or `UUID.randomUUID()` in Java (note: `gen_random_uuid()` is a built-in function in PostgreSQL 13+, though pgcrypto extension is still referenced in migrations for backward compatibility)
- Stored procedures use `PLPGSQL` language and include `RAISE INFO` for operational logging

## JPA/Hibernate Entity Conventions

### Naming Strategy
- A custom `SnakeCasePhysicalNamingStrategy` auto-converts camelCase Java field names to snake_case column names
- Configured via `quarkus.hibernate-orm.physical-naming-strategy=com.redhat.cloud.notifications.db.naming.SnakeCasePhysicalNamingStrategy`
- This strategy only affects **column names**; table names must be explicitly mapped

### Entity Class Structure
- Always annotate with `@Entity` and `@Table(name = "snake_case_table_name")`
- Entities live in `common/src/main/java/com/redhat/cloud/notifications/models/`
- Use `@JsonNaming(SnakeCaseStrategy.class)` for JSON serialization (legacy pattern, being replaced by DTOs)

### ID Generation
- Primary keys are `UUID` type
- Two patterns exist:
  1. `@Id @GeneratedValue private UUID id;` -- Hibernate generates the UUID (used by `Event`, `Application`, `BehaviorGroup`, `EventType`)
  2. `@Id private UUID id;` with manual generation in `additionalPrePersist()` -- used by `Endpoint` when the ID may be pre-assigned
- `EndpointProperties` subclasses share the parent `Endpoint` ID via `@MapsId` and `@OneToOne`
- For `NotificationHistory`, ID is not auto-generated (`@Id` only, no `@GeneratedValue`) because IDs are assigned externally before sending to Camel

### Timestamp Management
- Extend `CreationTimestamped` for entities with only a `created` field
- Extend `CreationUpdateTimestamped` for entities with both `created` and `updated` fields
- `@PrePersist` sets `created = LocalDateTime.now(UTC)` if null
- `@PreUpdate` sets `updated = LocalDateTime.now(UTC)`
- Override `additionalPrePersist()` for custom pre-persist logic (e.g., UUID generation in `Endpoint`)

### Relationship Mapping
- Use `FetchType.LAZY` for all `@ManyToOne` and `@OneToOne` relationships
- Use `@JoinColumn(name = "snake_case_fk_column")` to explicitly name FK columns
- Composite keys use `@EmbeddedId` with a separate `@Embeddable` ID class (e.g., `BehaviorGroupActionId`, `DrawerNotificationId`)
- Join table entities use `@MapsId` to map composite key fields to their relationships
- `@Embedded` is used for value objects like `CompositeEndpointType`

### Enum Mapping
- Use `@Enumerated(EnumType.STRING)` for all enum fields
- Custom converters exist for some types (e.g., `EndpointTypeConverter`, `HttpTypeConverter`)

### Sortable Fields
- Each sortable entity defines a `public static final Map<String, String> SORT_FIELDS` mapping API field names to HQL paths
- Example: `Map.of("name", "e.name", "created", "e.created")`

## Repository Pattern

### Structure
- Repositories are `@ApplicationScoped` CDI beans in `backend/src/main/java/com/redhat/cloud/notifications/db/repositories/`
- Inject `EntityManager` directly (not Spring Data-style interfaces)
- One repository per aggregate root (e.g., `EndpointRepository`, `BehaviorGroupRepository`, `EventRepository`)

### Query Patterns
- **HQL/JPQL is the primary query language** -- no named queries are used
- Inline HQL strings built with concatenation and conditional appends
- Use `entityManager.createQuery(hql, ResultType.class)` for type-safe queries
- Use `entityManager.createNativeQuery(sql)` only for upserts (`ON CONFLICT ... DO ...`) and complex SQL not expressible in HQL
- Always use named parameters (`:paramName`), never positional parameters
- Handle `NoResultException` by returning `null`, `Optional.empty()`, or throwing `NotFoundException`

### QueryBuilder (Internal DSL)
- `QueryBuilder` and `WhereBuilder` provide a fluent API for building conditional HQL queries
- Used for complex filter scenarios like `EndpointRepository.queryBuilderEndpointsPerType()`
- Supports `.limit()`, `.sort()`, `.build()`, `.buildCount()`

### Multi-tenancy Filtering
- **Prefer filtering by `orgId`** in queries -- this is the primary tenant isolation mechanism
- Pattern: `WHERE e.orgId = :orgId` for tenant-scoped entities
- System-level/default entities may use `WHERE e.orgId IS NULL` or `WHERE (e.orgId = :orgId OR e.orgId IS NULL)` to include both tenant and system records
- Default/system entities have `orgId = NULL`; tenant entities have a non-null `orgId`
- Never trust client-supplied orgId values; always extract from the authenticated identity

### Existence Checks
- Use `SELECT 1 FROM Entity WHERE ...` with `getSingleResult()` wrapped in try/catch `NoResultException`
- Or use `SELECT COUNT(*) FROM Entity WHERE ...` and compare to 0

### Deadlock Prevention
- Use `PESSIMISTIC_WRITE` lock mode before bulk updates to prevent deadlocks
- Pattern: lock existing rows, compute diff, delete removed items, insert new items
- Example in `BehaviorGroupRepository.updateBehaviorGroupActions()` and `updateBehaviorEventTypes()`

## Transaction Management

- Use `jakarta.transaction.Transactional` (JTA), not Spring's `@Transactional`
- Apply `@Transactional` at the repository method level, not at the class level
- Read-only methods do NOT use `@Transactional`
- Write operations (`persist`, `createQuery(...).executeUpdate()`, `delete`) require `@Transactional`
- When a method calls other `@Transactional` methods, the outer method must also be `@Transactional`
- After native SQL modifications that Hibernate is unaware of, call `entityManager.flush()` and `entityManager.refresh(entity)`

## Pagination and Filtering

### Query Object
- `Query` class binds JAX-RS query parameters: `limit` (1-200, default 20), `pageNumber`, `offset`, `sort_by`
- `offset` takes precedence over `pageNumber` if both are set
- Pagination uses `TypedQuery.setMaxResults(limit).setFirstResult(offset)`

### Sort
- `Sort.getSort(query, defaultSort, SORT_FIELDS)` validates and maps sort parameters
- Default sort format: `"created:DESC"` (field:direction)
- Secondary sort: when primary sort is not `created`, append `, e.created DESC` as tiebreaker

### Dynamic Filtering
- Filters are applied by conditionally appending HQL clauses with `AND`
- Parameter binding is conditional: only set parameters that correspond to appended clauses
- Separate `count()` and `getResults()` methods share the same filter-building logic via private helper methods (`addHqlConditions`, `setQueryParams`)

## Database Configuration

- Database: PostgreSQL
- Extension: `pgcrypto` (for `gen_random_uuid()` in older PostgreSQL versions; built-in since PostgreSQL 13)
- JSONB columns: configured with `quarkus.hibernate-orm.mapping.format.global=ignore` to decouple Jackson REST serialization from database JSONB serialization
- JSON column mapping: use `@JdbcTypeCode(SqlTypes.JSON)` for JSON/JSONB columns (e.g., `EventType.availableSeverities`)

## Properties Loading (Endpoint Inheritance)

- `Endpoint` properties are stored in separate tables per type (e.g., `endpoint_webhooks`, `camel_properties`)
- Properties are `@Transient` on the `Endpoint` entity and loaded via a separate batch query in `loadProperties()`
- Properties classes extend `EndpointProperties` (`@MappedSuperclass`) and share the parent `Endpoint` ID via `@MapsId`
- After persisting an `Endpoint`, explicitly call `endpoint.getProperties().setEndpoint(endpoint)` before persisting properties
