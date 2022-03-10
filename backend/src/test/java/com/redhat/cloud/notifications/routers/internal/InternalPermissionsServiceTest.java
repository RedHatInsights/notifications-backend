package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.CrudTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.routers.internal.models.InternalUserPermissions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalPermissionsServiceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void userAccess() {
        String appRole = "crc-app-team";
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header turnpikeAppDev = TestHelpers.createTurnpikeIdentityHeader("app-admin", appRole);

        String bundleId = CrudTestHelpers.createBundle(turnpikeAdminHeader, "test-permission-bundle", "Test permissions Bundle", 200).get();
        String appId = CrudTestHelpers.createApp(turnpikeAdminHeader, bundleId, "test-permission-app", "Test permissions App", 200).get();

        // admin - Has admin access and no applicationIds
        InternalUserPermissions permissions = given()
                .header(turnpikeAdminHeader)
                .when()
                .get("/internal/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);

        assertTrue(permissions.isAdmin());
        assertTrue(permissions.getApplicationIds().isEmpty());

        // App admin - no permissions are set yet, no admin and no applicationIds
        permissions = given()
                .header(turnpikeAppDev)
                .get("/internal/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);

        assertFalse(permissions.isAdmin());
        assertEquals(Set.of(), permissions.getApplicationIds());

        // Can't create an event type without the permission
        CrudTestHelpers.createEventType(turnpikeAppDev, appId, "my-event", "My event", "Event description", 403);

        // non admins can't create a role
        CrudTestHelpers.createInternalRoleAccess(turnpikeAppDev, appRole, appId, 403);

        // Give permissions to appRole over appId
        String appRoleInternalAccessId = CrudTestHelpers.createInternalRoleAccess(turnpikeAdminHeader, appRole, appId, 200).get();

        // Non admins can't create a role - even if they have permissions to an app
        CrudTestHelpers.createInternalRoleAccess(turnpikeAppDev, appRole, appId, 403);

        // App admin - no admin and applicationIds is [ appId ]
        permissions = given()
                .header(turnpikeAppDev)
                .get("/internal/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);

        assertFalse(permissions.isAdmin());
        assertEquals(Set.of(appId), permissions.getApplicationIds());

        // We can create the event type now
        String eventTypeId = CrudTestHelpers.createEventType(turnpikeAppDev, appId, "my-event", "My event", "Event description", 200).get();

        List<Map> roleAccessList = given()
                .header(turnpikeAdminHeader)
                .get("/internal/access")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(List.class);

        assertEquals(1, roleAccessList.size());

        // Give permissions to randomRole over appId
        CrudTestHelpers.createInternalRoleAccess(turnpikeAdminHeader, "random-role", appId, 200);

        roleAccessList = given()
                .header(turnpikeAdminHeader)
                .get("/internal/access")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().jsonPath().getList(".");

        assertEquals(2, roleAccessList.size());

        CrudTestHelpers.deleteInternalRoleAccess(turnpikeAdminHeader, appRoleInternalAccessId, 204);

        // permission removed
        permissions = given()
                .header(turnpikeAppDev)
                .get("/internal/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);

        assertFalse(permissions.isAdmin());
        assertEquals(Set.of(), permissions.getApplicationIds());

        // Without permissions we can't remove the event type
        CrudTestHelpers.deleteEventType(turnpikeAppDev, eventTypeId, null, 403);

        // but the admin can
        CrudTestHelpers.deleteEventType(turnpikeAdminHeader, eventTypeId, true, 200);
    }
}
