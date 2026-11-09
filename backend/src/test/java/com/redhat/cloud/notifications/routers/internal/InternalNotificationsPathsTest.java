package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * RHCLOUD-52372: the internal endpoints are served under the legacy "/internal" base path and under the
 * "/internal/notifications/v1/backend" base path. Both must behave the same, including authentication.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalNotificationsPathsTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void testRoleProtectedEndpointOnBothPaths() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        for (String basePath : new String[] {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND}) {
            given().header(adminIdentity).when().get(basePath + "/severities").then().statusCode(HttpStatus.SC_OK);
        }
    }

    @Test
    void testRoleProtectedEndpointRequiresIdentityOnBothPaths() {
        for (String basePath : new String[] {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND}) {
            given().when().get(basePath + "/severities").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
            given().when().get(basePath + "/templates").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
        }
    }

    @Test
    void testUserWithoutAdminRoleGetsTheSameStatusOnBothPaths() {
        Header userIdentity = TestHelpers.createTurnpikeIdentityHeader("user", "unknown-role");

        // The authorization result must not depend on the base path.
        int legacyStatus = given().header(userIdentity).when().get(API_INTERNAL + "/templates").then().extract().statusCode();
        int currentStatus = given().header(userIdentity).when().get(API_INTERNAL_NOTIFICATIONS_BACKEND + "/templates").then().extract().statusCode();
        assertEquals(legacyStatus, currentStatus);
    }

    /**
     * The "/gw", "/validation" and "/version" endpoints are allowed without an identity header on both base paths.
     */
    @Test
    void testUnauthenticatedEndpointsOnBothPaths() {
        for (String basePath : new String[] {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND}) {
            for (String endpoint : new String[] {"/version", "/gw/subscriptions/bundle/app", "/validation/baet"}) {
                int status = given().when().get(basePath + endpoint).then().extract().statusCode();
                assertNotEquals(HttpStatus.SC_UNAUTHORIZED, status, basePath + endpoint + " must not require an identity header");
            }
        }
    }

    /**
     * Only specific endpoints of the new base path are unauthenticated, never the whole base path.
     */
    @Test
    void testNewBasePathIsNotUnauthenticatedAsAWhole() {
        // Missing component segment: not a real endpoint, must not be allowlisted.
        given().when().get("/internal/notifications/v1/gw/subscriptions/bundle/app").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
        // Other engine/backend paths of the new base path require an identity.
        given().when().get(API_INTERNAL_NOTIFICATIONS_BACKEND + "/access/me").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
        given().when().get(API_INTERNAL_NOTIFICATIONS_BACKEND + "/cleanup/anything").then().statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    void testBothPathsReturnTheSameResponse() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        String legacy = given().header(adminIdentity).when().get(API_INTERNAL + "/severities").then().extract().asString();
        String current = given().header(adminIdentity).when().get(API_INTERNAL_NOTIFICATIONS_BACKEND + "/severities").then().extract().asString();
        assertEquals(legacy, current);
    }
}
