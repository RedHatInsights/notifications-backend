# Quarkus 3.40.1 Migration Summary

**Date:** 2026-10-14  
**Migration:** 3.33.4 → 3.40.1 (LTS)  
**Status:** ✅ Compilation successful, awaiting full test validation

## Changes Applied

- Updated `quarkus.platform.version` from `3.33.4` to `3.40.1` in root `pom.xml`

## Build Verification

### ✅ Successful Compilation
All modules compiled successfully:
- All 23 modules: BUILD SUCCESS
- Checkstyle validation: PASSED (0 violations)
- No new compilation errors introduced
- All existing warnings are pre-existing (MapStruct unmapped properties, deprecation warnings)

### ⏳ Test Execution
Full test suite could not be executed due to Docker/testcontainers environmental issue (unrelated to Quarkus upgrade):
```
Container startup failed for image postgres:15.2
device or resource busy
```

**Recommendation:** Run tests in a clean Docker environment or CI pipeline.

## Impact Analysis by Migration Guide Version

### 3.34 - Micrometer Prometheus ✅ SAFE
**Change:** Duplicate metric registration with different tags now throws exception  
**Our Usage:** Reviewed `KesselInventoryAuthorization.java` - uses correct pattern with dynamic tag VALUES (not conflicting tag definitions)  
**Action Required:** None

### 3.35 - Hibernate ORM 7.3 & REST Assured 6.0 ⚠️ NEEDS TESTING
**Changes:**
1. REST Assured upgraded 5.x → 6.0
2. `@Transactional` methods no longer auto-dispatch to worker threads (checks return type)
3. `getSingleResult()` stricter enforcement - throws on multiple results

**Our Usage:**
- REST Assured: Used in `backend` and `connector-email` tests
- `@Transactional + Uni`: No instances found (not using this pattern)
- `getSingleResult()`: 3 safe usages found:
  - `DrawerNotificationRepository.count()` - COUNT query (always single result)
  - `InternalRoleAccessRepository.findOneByApplicationUUID()` - LIMIT 1 + NoResultException handler
  - `X509CertificateRepository` - Unique constraint query + NoResultException handler

**Action Required:** Run full test suite to validate REST Assured compatibility

### 3.36 - TLS Registry ✅ NOT IMPACTED
**Change:** Provider classes relocated `io.quarkus.tls.runtime.*` → `io.quarkus.tls.*`  
**Our Usage:** Only using TLS config properties in test resources (no Java imports)  
**Action Required:** None

### 3.37 - Hibernate ORM 7.4 & PostgreSQL 14 ⚠️ VERIFY INFRA
**Changes:**
1. PostgreSQL minimum version: 13 → **14**
2. Query pagination/limit now processed in SQL
3. Unique constraints added to `@ElementCollection` sets
4. `NOT NULL` on `@CreationTimestamp`/`@UpdateTimestamp`

**Our Usage:**
- No `@ElementCollection` found
- No `@CreationTimestamp`/`@UpdateTimestamp` annotations found
- PostgreSQL version: **Must verify deployed environments are >= 14**

**Action Required:**
1. Verify PostgreSQL version in all environments (dev, test, staging, production)
2. Check Flyway migrations compatibility with new DDL constraints

### 3.38 - Elasticsearch, Multipart, Host Validation ✅ NOT IMPACTED
**Changes:**
1. Elasticsearch REST client major upgrade (Apache HTTP Client 4 → 5)
2. Multipart file upload security hardening
3. HTTP Host header validation for localhost

**Our Usage:**
- Not using Elasticsearch
- Not using multipart file uploads
- Not using localhost Host validation (production apps)

**Action Required:** None

### 3.39 - Quarkus Data (Panache) ✅ NOT IMPACTED
**Change:** Panache renamed to Quarkus Data with package/type changes  
**Our Usage:** Not using Panache  
**Action Required:** None

### 3.40 - LTS Continuation ✅ SAFE
**Change:** Direct continuation of 3.39, no additional breaking changes  
**Action Required:** None

## Code Review Findings

### Metrics Usage (Micrometer)
```java
// KesselInventoryAuthorization.java - SAFE pattern
Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);
permissionCheckTimer.stop(
    this.meterRegistry.timer(
        KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, 
        Tags.of(
            KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(),
            Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.name()
        )
    )
);
```
✅ Uses different tag VALUES for same tag KEYS - correct pattern, no duplicates

### Repository Query Methods
All `getSingleResult()` usages are safe:
1. COUNT queries guaranteed to return single result
2. LIMIT 1 queries with proper exception handling
3. Unique constraint queries with NoResultException catch blocks

## Recommendations

### Before Deployment

1. **✅ DONE:** Update Quarkus version in pom.xml
2. **✅ DONE:** Verify code compilation
3. **⏳ TODO:** Run full test suite (requires Docker environment)
4. **⏳ TODO:** Verify PostgreSQL version >= 14 in all environments
5. **⏳ TODO:** Review Flyway migration execution logs for DDL changes
6. **⏳ TODO:** Test REST Assured compatibility in backend tests
7. **⏳ TODO:** Run integration tests for all connectors

### Testing Checklist

```bash
# Full build with tests (once Docker is working)
./mvnw clean verify --no-transfer-progress

# Specific modules to focus on
./mvnw test -pl :notifications-backend --no-transfer-progress
./mvnw test -pl :notifications-connector-email --no-transfer-progress
./mvnw test -pl :notifications-engine --no-transfer-progress

# Check Flyway migrations
./mvnw clean verify -pl :notifications-backend -am --no-transfer-progress
```

### Monitoring After Deployment

1. Watch for Micrometer metrics registration errors in logs
2. Monitor Hibernate query behavior (especially pagination/limits)
3. Check for any PostgreSQL compatibility issues
4. Verify REST endpoints still function correctly

## PostgreSQL Version Requirement

⚠️ **CRITICAL:** Quarkus 3.37+ requires PostgreSQL >= 14

**Action Items:**
- [ ] Verify production PostgreSQL version
- [ ] Verify staging PostgreSQL version  
- [ ] Verify test environment PostgreSQL version
- [ ] Update deployment documentation if needed

## Migration Guide References

- [3.34](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.34) - Micrometer changes
- [3.35](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.35) - Hibernate ORM 7.3, REST Assured 6.0
- [3.36](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.36) - TLS Registry
- [3.37](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.37) - Hibernate ORM 7.4, PostgreSQL 14
- [3.38](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.38) - Elasticsearch, security
- [3.39](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.39) - Quarkus Data
- [3.40](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.40) - LTS release

## Rollback Plan

If issues are discovered:
```xml
<!-- Revert to previous version -->
<quarkus.platform.version>3.33.4</quarkus.platform.version>
```

## Next Steps

1. Fix Docker/testcontainers environment
2. Run full test suite
3. Verify PostgreSQL versions in all environments
4. Create PR with this migration
5. Run CI/CD pipeline
6. Monitor initial deployment closely
