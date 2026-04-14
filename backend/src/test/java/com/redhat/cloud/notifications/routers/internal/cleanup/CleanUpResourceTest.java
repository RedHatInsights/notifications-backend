package com.redhat.cloud.notifications.routers.internal.cleanup;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class CleanUpResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Test
    void testDrawerIntegrationDelete() {

        for (int i = 0; i < 15; i++) {
            resourceHelpers.createEndpoint("123456", "123456", EndpointType.DRAWER);
        }
        String query = "SELECT e FROM Endpoint e WHERE e.orgId = :orgId";
        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("orgId", 123456)
            .getResultList();

        assertEquals(15, endpoints.size());

        given()
                .basePath(Constants.API_INTERNAL)
                .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
                .when()
                .contentType(JSON)
                .body(10)
                .post("/cleanup/drawer")
                .then()
                .statusCode(200);

        endpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("orgId", 123456)
            .getResultList();
        assertEquals(5, endpoints.size());

        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(JSON)
            .body(10)
            .post("/cleanup/drawer")
            .then()
            .statusCode(200);

        endpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("orgId", 123456)
            .getResultList();
        assertEquals(0, endpoints.size());
    }

    @Test
    void testInventoryEventsDelete() {

        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(JSON)
            .body(10)
            .post("/cleanup/inventory_events")
            .then()
            .statusCode(200);
    }

    @Test
    void testEmailAggregationDelete() {

        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(JSON)
            .body(10)
            .post("/cleanup/email_aggregation")
            .then()
            .statusCode(200);
    }

    @Test
    void testSeverityOrderBackfill() {
        // Create test fixtures
        Bundle bundle = resourceHelpers.createBundle("backfill-bundle", "Backfill Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "backfill-app", "Backfill App");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "backfill-event-type", "Backfill Event Type", "desc");

        // Insert events with different severities via native SQL
        String orgId = "backfill-test-org";
        insertEvent(orgId, bundle, app, eventType, Severity.CRITICAL);
        insertEvent(orgId, bundle, app, eventType, Severity.IMPORTANT);
        insertEvent(orgId, bundle, app, eventType, Severity.LOW);

        // Simulate pre-migration rows by NULLing out severity_order
        nullOutSeverityOrder(orgId);

        // Verify severity_order is NULL
        long nullCount = countNullSeverityOrder(orgId);
        assertEquals(3, nullCount);

        // Backfill only 2 rows
        int updated = given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(JSON)
            .body(2)
            .post("/cleanup/severity_order_backfill")
            .then()
            .statusCode(200)
            .extract().as(Integer.class);

        assertEquals(2, updated);
        assertEquals(1, countNullSeverityOrder(orgId));

        // Backfill the remaining row
        updated = given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(JSON)
            .body(10)
            .post("/cleanup/severity_order_backfill")
            .then()
            .statusCode(200)
            .extract().as(Integer.class);

        assertEquals(1, updated);
        assertEquals(0, countNullSeverityOrder(orgId));

        // Verify the computed values are correct
        List<Object[]> results = entityManager.createNativeQuery(
            "SELECT severity, severity_order FROM event WHERE org_id = :orgId ORDER BY severity_order")
            .setParameter("orgId", orgId)
            .getResultList();

        assertEquals(3, results.size());
        assertEquals("CRITICAL", results.get(0)[0]);
        assertEquals((short) 10, ((Number) results.get(0)[1]).shortValue());
        assertEquals("IMPORTANT", results.get(1)[0]);
        assertEquals((short) 20, ((Number) results.get(1)[1]).shortValue());
        assertEquals("LOW", results.get(2)[0]);
        assertEquals((short) 40, ((Number) results.get(2)[1]).shortValue());
    }

    @Transactional
    void insertEvent(String orgId, Bundle bundle, Application app, EventType eventType, Severity severity) {
        String sql = "INSERT INTO event (id, org_id, account_id, application_id, application_display_name, " +
            "bundle_id, bundle_display_name, event_type_id, event_type_display_name, has_authorization_criterion, " +
            "created, severity) " +
            "VALUES (:id, :orgId, :accountId, :applicationId, :applicationDisplayName, :bundleId, " +
            ":bundleDisplayName, :eventTypeId, :eventTypeDisplayName, false, NOW(), :severity)";
        entityManager.createNativeQuery(sql)
            .setParameter("id", UUID.randomUUID())
            .setParameter("orgId", orgId)
            .setParameter("accountId", "backfill-account")
            .setParameter("applicationId", app.getId())
            .setParameter("applicationDisplayName", app.getDisplayName())
            .setParameter("bundleId", bundle.getId())
            .setParameter("bundleDisplayName", bundle.getDisplayName())
            .setParameter("eventTypeId", eventType.getId())
            .setParameter("eventTypeDisplayName", eventType.getDisplayName())
            .setParameter("severity", severity.name())
            .executeUpdate();
    }

    @Transactional
    void nullOutSeverityOrder(String orgId) {
        entityManager.createNativeQuery("UPDATE event SET severity_order = NULL WHERE org_id = :orgId")
            .setParameter("orgId", orgId)
            .executeUpdate();
    }

    private long countNullSeverityOrder(String orgId) {
        return ((Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM event WHERE org_id = :orgId AND severity_order IS NULL")
            .setParameter("orgId", orgId)
            .getSingleResult()).longValue();
    }
}
