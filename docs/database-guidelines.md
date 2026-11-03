# Database Guidelines

## Flyway Migration Conventions

### Versioning
- Use `V1.<next>.0__` format where `<next>` is the next integer after the highest existing migration (currently V1.133.0).
- Reserve patch versions (V1.x.1, V1.x.2) only for hotfix follow-ups to a migration within the same PR. Prefer a new minor version.
- Migrations live in `database/src/main/resources/db/migration/`.

### Naming
- Format: `V1.<N>.0__<TICKET>_<snake_case_description>.sql`
- Include the Jira ticket ID when one exists: `V1.134.0__RHCLOUD-12345_add_foo_column.sql`
- When no ticket exists, use a descriptive name: `V1.134.0__add_foo_column.sql`
- Use snake_case in the description portion after the ticket reference.

### Content Rules
- Each migration must be idempotent-safe or strictly additive; Flyway runs migrations once and records checksums.
- Prefer `CREATE INDEX` as a separate migration from `ALTER TABLE` when both are needed -- keeps migrations small and reviewable.
- Use `IF NOT EXISTS` / `IF EXISTS` for index and table creation/deletion to prevent failures on re-runs in dev.
- Include `COMMENT ON` for non-obvious indexes or stored procedures (see V1.118.0 for example).

## Data Initialization Placement

When introducing features that require initial or seed data, clearly distinguish between deterministic initialization in Flyway SQL migrations and runtime-dependent initialization in Java application code:

1. **Deterministic data initialization belongs in Flyway SQL migrations**:
   - System/seed data with known values (e.g., system workspace with a fixed UUID, default configurations, initial enum-like rows) should be created in migration scripts. If the data can be computed at schema-migration time without external service calls, it belongs in SQL.
   - Example: Migration `V1.143.0` creates the system workspace row directly via `INSERT INTO workspace` with a deterministic name-derived UUID (`UUID.nameUUIDFromBytes("notifications-system-workspace"...)`).

2. **Runtime-dependent data initialization belongs in Java code**:
   - Operations that require external service calls (e.g., fetching workspace UUIDs from RBAC, resolving org-specific data) must reside in Java endpoints, startup routines, or background jobs since external service calls cannot run during schema migration.
   - Example: Org workspace assignment queries RBAC dynamically and creates org-specific workspace records through Java repository calls during the bootstrap process (`POST /internal/workspace/bootstrap`).

3. **Co-located bulk updates (Corollary)**:
   - When a migration creates a seed entity (e.g., system workspace), any bulk updates that depend only on that entity and existing table data (e.g., assigning existing system endpoints to the system workspace) should happen in the same migration script, not in a separate Java bootstrap step.
   - Example: In migration `V1.143.0`, right after creating the system workspace, an `UPDATE endpoints SET workspace_id = ... WHERE org_id IS NULL` assigns all existing system endpoints to the system workspace in the same migration.

## Schema Conventions

### Tables and Columns
- Table names: lowercase snake_case, plural for entity tables (`endpoints`, `applications`, `bundles`) or singular for join/status tables (`behavior_group`, `event_type`, `drawer_read_status`).
- Column names: lowercase snake_case in SQL. Hibernate auto-converts via `SnakeCasePhysicalNamingStrategy`.
- Primary keys: UUID type generated with `public.gen_random_uuid()` (requires `pgcrypto` extension).
- Timestamp columns: use `timestamp with time zone` in SQL. Entities use `LocalDateTime` with UTC zone.
- Multi-tenant filtering: include `org_id TEXT NOT NULL` on tenant-scoped tables. Queries must always filter by `orgId`.

### Naming Conventions for Constraints and Indexes
- Foreign keys: `fk_<table>_<column>` (e.g., `fk_event_event_type_id`).
- Primary keys (explicit): `pk_<table>` (e.g., `pk_endpoint_event_type`).
- Indexes: `ix_<table>_<columns>` (e.g., `ix_event_org_id_created_authorization_criterion`).
- Unique constraints: `<table>_<columns>_key` (e.g., `behavior_group_bundle_id_display_name_org_id_key`).
- Foreign keys should use `ON DELETE CASCADE` unless the relationship requires `ON DELETE SET NULL` (e.g., notification_history to endpoints).

## Entity Conventions

### Base Classes
- Entities with `created`/`updated` timestamps extend `CreationUpdateTimestamped` (which extends `CreationTimestamped`).
- `@PrePersist` sets `created` to `LocalDateTime.now(UTC)` automatically. `@PreUpdate` sets `updated`.
- Override `additionalPrePersist()` for entity-specific initialization (e.g., generating UUIDs in `Endpoint`).

### Mapping Patterns
- Annotate every entity with `@Entity` and explicit `@Table(name = "...")`.
- Use `@JsonNaming(SnakeCaseStrategy.class)` on entities exposed via JSON API.
- Use `@GeneratedValue` on UUID `@Id` fields. Avoid sequences for new tables -- UUIDs are the standard.
- Composite keys use `@EmbeddedId` with a separate `@Embeddable` ID class (see `DrawerReadStatus` / `EndpointEventType`).
- Mark FK relationships `FetchType.LAZY` by default. Use `JOIN FETCH` in queries when eager loading is needed.
- Properties subtypes (WebhookProperties, CamelProperties, PagerDutyProperties) extend `EndpointProperties` with `@MapsId` sharing the parent Endpoint's UUID.

### Equals/HashCode
- Implement `equals` and `hashCode` using only the `id` field via `Objects.equals`/`Objects.hash`.

## Repository Conventions

### Structure
- This codebase does NOT use Panache. Repositories are plain CDI beans (`@ApplicationScoped`) that inject `EntityManager`.
- Repository classes live in `<module>/src/main/java/.../db/repositories/`.
- Use HQL/JPQL for queries, not Criteria API. Native SQL is acceptable only for PostgreSQL-specific operations (array operations, `LIMIT` in subqueries, `ON CONFLICT`).

### Query Patterns
- Prefer string-built HQL with named parameters (`:paramName`), never string concatenation of user input.
- Use the `QueryBuilder`/`WhereBuilder` fluent API (in `com.redhat.cloud.notifications.db.builder`) for complex conditional queries with optional filters.
- Sorting: entities define a static `SORT_FIELDS` map that maps API field names to HQL paths. Use `Sort.getSort()` to validate sort parameters against this map.
- Pagination: use `TypedQuery.setMaxResults()` and `setFirstResult()` via `Query.Limit`.
- For large result sets, fetch IDs first, then load full entities by ID list (see `EventRepository.getEvents` two-query pattern).

### Transactions
- Annotate write methods with `@Transactional` (Jakarta). Read-only methods do not need it.
- Keep transactions short -- do validation before `@Transactional` methods when possible.

## Testing

### Test Infrastructure
- Tests use PostgreSQL 16 via Testcontainers (configured in `TestLifecycleManager`).
- Flyway runs migrations at test startup (`quarkus.flyway.migrate-at-start=true`).
- Database tests extend `DbIsolatedTest`, which cleans all tables via `DbCleaner` before and after each test.
- Use `ResourceHelpers` to create test fixtures (bundles, applications, event types, endpoints).

## Verification

```sh
# Validate migration files parse correctly (Flyway check via Quarkus startup)
./mvnw -pl backend quarkus:dev -Dquarkus.flyway.migrate-at-start=true -Dquarkus.http.port=0

# Run database-related tests
./mvnw -pl backend test -Dtest="*RepositoryTest"

# List all migrations in order
ls -1 database/src/main/resources/db/migration/ | sort -V

# Check for duplicate migration version numbers
ls database/src/main/resources/db/migration/ | grep -oP 'V\d+\.\d+\.\d+' | sort | uniq -d

# Compile the database module to verify migration resources are included
./mvnw -pl database compile
```
