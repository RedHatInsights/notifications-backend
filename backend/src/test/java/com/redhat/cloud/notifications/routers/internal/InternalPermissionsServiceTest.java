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
        String appDisplayName = "Test permissions App";
        String appId = CrudTestHelpers.createApp(turnpikeAdminHeader, bundleId, "test-permission-app", appDisplayName, null, 200).get();

        // admin - Has admin access and no applicationIds
        InternalUserPermissions permissions = permissions(turnpikeAdminHeader);

        assertTrue(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());

        // App admin - no permissions are set yet, no admin and no applicationIds
        permissions = permissions(turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());

        // Can't create an event type without the permission
        CrudTestHelpers.createEventType(turnpikeAppDev, appId, "my-event", "My event", "Event description", 403);

        // non admins can't create a role
        CrudTestHelpers.createInternalRoleAccess(turnpikeAppDev, appRole, appId, 403);

        // Give permissions to appRole over appId
        String appRoleInternalAccessId = CrudTestHelpers.createInternalRoleAccess(turnpikeAdminHeader, appRole, appId, 200).get();

        // Non admins can't create a role - even if they have permissions to an app
        CrudTestHelpers.createInternalRoleAccess(turnpikeAppDev, appRole, appId, 403);

        // App admin - no admin and applicationIds is [ appId ]
        permissions = permissions(turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertEquals(List.of(new InternalUserPermissions.Application(appId, appDisplayName)), permissions.getApplications());

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
        permissions = permissions(turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());

        // Without permissions we can't remove the event type
        CrudTestHelpers.deleteEventType(turnpikeAppDev, eventTypeId, null, 403);

        // but the admin can
        CrudTestHelpers.deleteEventType(turnpikeAdminHeader, eventTypeId, true, 200);
    }

    @Test
    void createAppWithPermissions() {
        String appRole = "crc-app-team";
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header turnpikeAppDev = TestHelpers.createTurnpikeIdentityHeader("app-admin", appRole);

        String bundleId = CrudTestHelpers.createBundle(turnpikeAdminHeader, "test-with-permission-bundle", "Test permissions Bundle", 200).get();

        // regular user can't create apps without a role
        CrudTestHelpers.createApp(
                turnpikeAppDev,
                bundleId,
                "will-fail",
                "will-faill",
                null,
                403
        );

        // regular user can't create aps with a role they do not own
        CrudTestHelpers.createApp(
                turnpikeAppDev,
                bundleId,
                "will-fail",
                "will-faill",
                "policies-team",
                403
        );

        // regular users can create apps with a role they own
        String appDisplayName = "Test permissions App";
        String appId = CrudTestHelpers.createApp(
                turnpikeAppDev,
                bundleId,
                "app-with-role",
                appDisplayName,
                appRole,
                200
        ).get();

        InternalUserPermissions permissions = permissions(turnpikeAppDev);
        assertEquals(List.of(new InternalUserPermissions.Application(appId, appDisplayName)), permissions.getApplications());

        // admins can create apps without a role
        CrudTestHelpers.createApp(
                turnpikeAdminHeader,
                bundleId,
                "i-will-succeed-no-role",
                "i-will-succeed-no-role",
                null,
                200
        );

        // admins can create apps with any role
        CrudTestHelpers.createApp(
                turnpikeAdminHeader,
                bundleId,
                "i-will-succeed-with-role",
                "i-will-succeed-with-role",
                "policies-team",
                200
        );
    }

    InternalUserPermissions permissions(Header auth) {
        return given()
                .header(auth)
                .get("/internal/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);
    }
}
