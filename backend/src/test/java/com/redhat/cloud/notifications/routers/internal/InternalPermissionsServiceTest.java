package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.CrudTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.routers.internal.models.InternalApplicationUserPermission;
import com.redhat.cloud.notifications.routers.internal.models.InternalUserPermissions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
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

    private static final int OK = 200;
    private static final int FORBIDDEN = 403;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void userAccess(String basePath) {
        String appRole = "crc-app-team";
        String otherRole = "other-role";
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header turnpikeAppDev = TestHelpers.createTurnpikeIdentityHeader("app-admin", appRole, otherRole);

        String bundleId = CrudTestHelpers.createBundle(basePath, turnpikeAdminHeader, "test-permission-bundle", "Test permissions Bundle", 200).get();
        String appDisplayName = "Test permissions App";
        String appId = CrudTestHelpers.createApp(basePath, turnpikeAdminHeader, bundleId, "test-permission-app", appDisplayName, null, 200).get();

        // admin - Has admin access and no applicationIds and no roles.
        InternalUserPermissions permissions = permissions(basePath, turnpikeAdminHeader);

        assertTrue(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());
        assertTrue(permissions.getRoles().isEmpty());

        // App admin - no permissions are set yet, no admin, no applicationIds but has roles
        permissions = permissions(basePath, turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());
        assertEquals(List.of(appRole, otherRole), permissions.getRoles());

        // Can't create an event type without the permission
        CrudTestHelpers.createEventType(basePath, turnpikeAppDev, appId, "my-event", "My event", "Event description", false, false, 403);

        // non admins can't create a role
        CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAppDev, appRole, appId, 403);

        // Give permissions to appRole over appId
        String appRoleInternalAccessId = CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAdminHeader, appRole, appId, 200).get();

        // Non admins can't create a role - even if they have permissions to an app
        CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAppDev, appRole, appId, 403);

        // App admin - no admin, applicationIds is [ appId ] and has roles
        permissions = permissions(basePath, turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertEquals(List.of(new InternalUserPermissions.Application(UUID.fromString(appId), appDisplayName)), permissions.getApplications());
        assertEquals(List.of(appRole, otherRole), permissions.getRoles());

        // We can create the event type now
        String eventTypeId = CrudTestHelpers.createEventType(basePath, turnpikeAppDev, appId, "my-event", "My event", "Event description", false, false, 200).get();

        List<Map> roleAccessList = given()
                .header(turnpikeAdminHeader)
                .get(basePath + "/access")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(List.class);

        assertEquals(1, roleAccessList.size());

        // Give permissions to randomRole over appId
        CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAdminHeader, "random-role", appId, 200);

        roleAccessList = given()
                .header(turnpikeAdminHeader)
                .get(basePath + "/access")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().jsonPath().getList(".");

        assertEquals(2, roleAccessList.size());

        CrudTestHelpers.deleteInternalRoleAccess(basePath, turnpikeAdminHeader, appRoleInternalAccessId, 204);

        // permission removed
        permissions = permissions(basePath, turnpikeAppDev);

        assertFalse(permissions.isAdmin());
        assertTrue(permissions.getApplications().isEmpty());

        // Without permissions we can't remove the event type
        CrudTestHelpers.deleteEventType(basePath, turnpikeAppDev, eventTypeId, null, 403);

        // but the admin can
        CrudTestHelpers.deleteEventType(basePath, turnpikeAdminHeader, eventTypeId, true, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void createAppWithPermissions(String basePath) {
        String appRole = "crc-app-team";
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header turnpikeAppDev = TestHelpers.createTurnpikeIdentityHeader("app-admin", appRole);

        String bundleId = CrudTestHelpers.createBundle(basePath, turnpikeAdminHeader, "test-with-permission-bundle", "Test permissions Bundle", 200).get();

        // regular user can't create apps without a role
        CrudTestHelpers.createApp(basePath,
                turnpikeAppDev,
                bundleId,
                "will-fail",
                "will-faill",
                null,
                403
        );

        // regular user can't create aps with a role they do not own
        CrudTestHelpers.createApp(basePath,
                turnpikeAppDev,
                bundleId,
                "will-fail",
                "will-faill",
                "policies-team",
                403
        );

        // regular users can create apps with a role they own
        String appDisplayName = "Test permissions App";
        String appId = CrudTestHelpers.createApp(basePath,
                turnpikeAppDev,
                bundleId,
                "app-with-role",
                appDisplayName,
                appRole,
                200
        ).get();

        InternalUserPermissions permissions = permissions(basePath, turnpikeAppDev);
        assertEquals(List.of(new InternalUserPermissions.Application(UUID.fromString(appId), appDisplayName)), permissions.getApplications());

        // admins can create apps without a role
        CrudTestHelpers.createApp(basePath,
                turnpikeAdminHeader,
                bundleId,
                "i-will-succeed-no-role",
                "i-will-succeed-no-role",
                null,
                200
        );

        // admins can create apps with any role
        CrudTestHelpers.createApp(basePath,
                turnpikeAdminHeader,
                bundleId,
                "i-will-succeed-with-role",
                "i-will-succeed-with-role",
                "policies-team",
                200
        );
    }

    InternalUserPermissions permissions(String basePath, Header auth) {
        return given()
                .header(auth)
                .get(basePath + "/access/me")
                .then()
                .contentType(JSON)
                .statusCode(200)
                .extract().as(InternalUserPermissions.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    public void accessListTest(String basePath) {
        String appRole = "Acrc-app-team";
        String otherRole = "Bcrc-other-team-role";
        Header turnpikeAdminHeader = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        Header turnpikeAppDev = TestHelpers.createTurnpikeIdentityHeader("app-admin", appRole);

        String bundleId = CrudTestHelpers.createBundle(basePath, turnpikeAdminHeader, "test-access-list-bundle", "Test access list Bundle", 200).get();
        String appDisplayName = "Test access list App";
        String appId = CrudTestHelpers.createApp(basePath, turnpikeAdminHeader, bundleId, "test-access-list-app", appDisplayName, null, 200).get();

        // app devs can't access it
        CrudTestHelpers.getAccessList(basePath, turnpikeAppDev, FORBIDDEN);

        // admins can access it
        List<InternalApplicationUserPermission> accessList = CrudTestHelpers.getAccessList(basePath, turnpikeAdminHeader, OK).get();

        // appId is not in the list
        assertEquals(
                0,
                accessList.stream().filter(a -> a.applicationId.toString().equals(appId)).count()
        );

        CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAdminHeader, appRole, appId, OK);
        accessList = CrudTestHelpers.getAccessList(basePath, turnpikeAdminHeader, OK).get();

        // It should be in the list now
        List<InternalApplicationUserPermission> filtered = accessList.stream().filter(a -> a.applicationId.toString().equals(appId)).collect(Collectors.toList());
        assertEquals(
                1,
                filtered.size()
        );

        assertEquals(appId, filtered.get(0).applicationId.toString());
        assertEquals(appDisplayName, filtered.get(0).applicationDisplayName);
        assertEquals(appRole, filtered.get(0).role);

        // Adding other role to the same app
        CrudTestHelpers.createInternalRoleAccess(basePath, turnpikeAdminHeader, otherRole, appId, OK);
        accessList = CrudTestHelpers.getAccessList(basePath, turnpikeAdminHeader, OK).get();
        filtered = accessList.stream().filter(a -> a.applicationId.toString().equals(appId)).sorted(Comparator.comparing(t -> t.role)).collect(Collectors.toList());
        assertEquals(
                2,
                filtered.size()
        );

        assertEquals(appId, filtered.get(0).applicationId.toString());
        assertEquals(appDisplayName, filtered.get(0).applicationDisplayName);
        assertEquals(appRole, filtered.get(0).role);

        assertEquals(appId, filtered.get(1).applicationId.toString());
        assertEquals(appDisplayName, filtered.get(1).applicationDisplayName);
        assertEquals(otherRole, filtered.get(1).role);
    }
}
