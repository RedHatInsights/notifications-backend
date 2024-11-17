package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.time.LocalTime;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class OrgConfigResourceTest extends DbIsolatedTest {
    /**
     * Mocked the backend's configuration so that the {@link KesselTestHelper}
     * can be used.
     */
    @InjectMock
    BackendConfig backendConfig;

    /**
     * Mocked Kessel's check client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    CheckClient checkClient;

    /**
     * Mocked RBAC's workspace utilities so that the {@link KesselTestHelper}
     * can be used.
     */
    @InjectMock
    WorkspaceUtils workspaceUtils;

    @Inject
    EntityManager entityManager;

    @Inject
    KesselTestHelper kesselTestHelper;

    /**
     * Mocked Kessel's lookup client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    LookupClient lookupClient;

    static final LocalTime TIME = LocalTime.of(10, 00);

    public static final String ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL = "/org-config/daily-digest/time-preference";
    String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
    Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

    String testMinIdentityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);

    @BeforeEach
    @Transactional
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        MockServerConfig.addMockRbacAccess(testMinIdentityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        // Clean up the aggregations since we are using the "DEFAULT_*"
        // organizations.
        this.entityManager
            .createQuery("DELETE FROM AggregationOrgConfig")
            .executeUpdate();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSaveDailyDigestTimePreference(final boolean isKesselRelationsApiEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled);

        // check regular parameter
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.DAILY_DIGEST_PREFERENCE_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        recordDefaultDailyDigestTimePreference();

        // check request without body
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testMinuteFilter(final boolean isKesselRelationsApiEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled);

        final String ERROR_MESSAGE_WRONG_MINUTE_VALUE = "Accepted minute values are: 00, 15, 30, 45.";

        Header testMinIdentityHeader = TestHelpers.createRHIdentityHeader(testMinIdentityHeaderValue);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.DAILY_DIGEST_PREFERENCE_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 00), Response.Status.NO_CONTENT.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 15), Response.Status.NO_CONTENT.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 30), Response.Status.NO_CONTENT.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 45), Response.Status.NO_CONTENT.getStatusCode());
        assertEquals(ERROR_MESSAGE_WRONG_MINUTE_VALUE, recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 10), Response.Status.BAD_REQUEST.getStatusCode()));
        assertEquals(ERROR_MESSAGE_WRONG_MINUTE_VALUE, recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 55), Response.Status.BAD_REQUEST.getStatusCode()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetDailyDigestTimePreference(final boolean isKesselRelationsApiEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselRelationsApiEnabled);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.DAILY_DIGEST_PREFERENCE_VIEW, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());

        LocalTime foundedPreference = given()
                .header(identityHeader)
                .when()
                .contentType(TEXT)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().as(LocalTime.class);
        assertEquals(LocalTime.MIDNIGHT, foundedPreference);

        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.DAILY_DIGEST_PREFERENCE_EDIT, ResourceType.WORKSPACE, KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID.toString());
        recordDefaultDailyDigestTimePreference();

        foundedPreference = given()
                .header(identityHeader)
                .when()
                .contentType(TEXT)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().as(LocalTime.class);
        assertEquals(TIME, foundedPreference);
    }

    private void recordDefaultDailyDigestTimePreference() {
        recordDailyDigestTimePreference(identityHeader, TIME, Response.Status.NO_CONTENT.getStatusCode());
    }

    private String recordDailyDigestTimePreference(Header header, LocalTime time, int expectedReturnCode) {
        return given()
                 .header(header)
                 .when()
                 .contentType(JSON)
                 .body(time)
                 .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                 .then()
                 .statusCode(expectedReturnCode).extract().asString();
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = initRbacMock(DEFAULT_USER + "-no-access", NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock(DEFAULT_USER + "-read-access", READ_ACCESS);

        given()
            .header(noAccessIdentityHeader)
            .when()
            .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(noAccessIdentityHeader)
            .when()
            .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(readAccessIdentityHeader)
            .when()
            .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_OK);

        given()
            .header(readAccessIdentityHeader)
            .when()
            .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }


    /**
     * Test that when using Kessel, if the user is not authorized to send
     * requests to the "OrgConfigResource"'s endpoints, an {@link HttpStatus#SC_UNAUTHORIZED}
     * response is returned.
     */
    @Test
    void testKesselUnauthorized() {
        this.kesselTestHelper.mockKesselRelations(true);
        this.kesselTestHelper.mockDefaultWorkspaceId(DEFAULT_ORG_ID);

        // Get the time preferences.
        given()
            .header(this.identityHeader)
            .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Attempt saving the itme preferences.
        given()
            .header(this.identityHeader)
            .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    private Header initRbacMock(final String username, final MockServerConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }
}
