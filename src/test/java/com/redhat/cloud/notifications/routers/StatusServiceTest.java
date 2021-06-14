package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.Status;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.Constants.INTERNAL;
import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.TestConstants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.models.Status.MAINTENANCE;
import static com.redhat.cloud.notifications.models.Status.UP;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class StatusServiceTest extends DbIsolatedTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    public void testValidCurrentStatus() {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo("tenant", "username");
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        // The test must not be run with a cached status from another test.
        clearCachedStatus();

        /*
         * First, let's check that all APIs are available.
         * We won't test /health because it's always DOWN and returns 503 during tests.
         */
        getMetrics();
        getBundles();
        getEndpoints(identityHeader, 200);
        getEventTypes(identityHeader, 200);

        // Now let's see if the current status is UP.
        getCurrentStatus(identityHeader, UP.name(), null, null);

        // Let's change that to MAINTENANCE with start and end times.
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(1L);
        putCurrentStatus(MAINTENANCE, startTime, endTime, 204);

        // The cache is cleared again because we don't want to wait for the normal expiration delay.
        clearCachedStatus();

        // Retrieving the current status should reflect that change.
        getCurrentStatus(identityHeader, MAINTENANCE.name(), startTime.toString(), endTime.toString());

        // The maintenance is on but /internal and /metrics should still be available.
        getMetrics();
        getBundles();

        // On the other hand, /api/integrations and /api/notifications should return 503.
        getEndpoints(identityHeader, 503);
        getEventTypes(identityHeader, 503);

        // We don't want other tests to be run with maintenance mode on.
        clearCachedStatus();
    }

    @Test
    public void testInvalidCurrentStatus() {
        // Null values.
        putCurrentStatus(MAINTENANCE, null, LocalDateTime.now(), 400);
        putCurrentStatus(MAINTENANCE, LocalDateTime.now(), null, 400);
        putCurrentStatus(MAINTENANCE, null, null, 400);
        // End time before start time.
        putCurrentStatus(MAINTENANCE, LocalDateTime.now(), LocalDateTime.now().minusHours(1L), 400);
    }

    @CacheInvalidate(cacheName = "maintenance")
    void clearCachedStatus() {
        /*
         * This would normally happen after a certain duration fixed in application.properties with the
         * quarkus.cache.caffeine.maintenance.expire-after-write key.
         */
    }

    private void getMetrics() {
        given()
                .when()
                .get("/metrics")
                .then()
                .statusCode(200);
    }

    private void getBundles() {
        given()
                .basePath(INTERNAL)
                .when()
                .get("/bundles")
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private void getEndpoints(Header identityHeader, int expectedStatusCode) {
        given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(expectedStatusCode);
    }

    private void getEventTypes(Header identityHeader, int expectedStatusCode) {
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/notifications/eventTypes")
                .then()
                .statusCode(expectedStatusCode);
    }

    private void getCurrentStatus(Header identityHeader, String expectedStatus, String expectedStartTime, String expectedEndTime) {
        String responseBody = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .when()
                .get("/status")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();

        JsonObject jsonCurrentStatus = new JsonObject(responseBody);
        jsonCurrentStatus.mapTo(CurrentStatus.class);
        assertEquals(expectedStatus, jsonCurrentStatus.getString("status"));
        if (expectedStartTime == null) {
            assertNull(jsonCurrentStatus.getString("start_time"));
        } else {
            // Jackson serializes LocalDateTime with a precision slightly smaller than LocalDateTime.toString().
            assertTrue(expectedStartTime.startsWith(jsonCurrentStatus.getString("start_time")));
        }
        if (expectedEndTime == null) {
            assertNull(jsonCurrentStatus.getString("end_time"));
        } else {
            // Jackson serializes LocalDateTime with a precision slightly smaller than LocalDateTime.toString().
            assertTrue(expectedEndTime.startsWith(jsonCurrentStatus.getString("end_time")));
        }
    }

    private void putCurrentStatus(Status status, LocalDateTime startTime, LocalDateTime endTime, int expectedStatusCode) {
        CurrentStatus currentStatus = new CurrentStatus();
        currentStatus.setStatus(status);
        currentStatus.setStartTime(startTime);
        currentStatus.setEndTime(endTime);

        given()
                .basePath(INTERNAL)
                .contentType(JSON)
                .body(Json.encode(currentStatus))
                .when()
                .put("/status")
                .then()
                .statusCode(expectedStatusCode);
    }
}
