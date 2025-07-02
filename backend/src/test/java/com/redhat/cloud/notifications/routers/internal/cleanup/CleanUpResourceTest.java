package com.redhat.cloud.notifications.routers.internal.cleanup;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.util.List;

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
}
