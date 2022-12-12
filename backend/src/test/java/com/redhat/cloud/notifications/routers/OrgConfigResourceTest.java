package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.ws.rs.core.Response;
import java.time.LocalTime;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class OrgConfigResourceTest extends DbIsolatedTest {

    static final LocalTime TIME = LocalTime.of(10, 00);

    public static final String ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL = "/org-config/daily-digest/time-preference";
    String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("empty", "empty", "user");
    Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

    String testMinIdentityHeaderValue = TestHelpers.encodeRHIdentityInfo("empty", "oneOrgId", "user");

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        MockServerConfig.addMockRbacAccess(testMinIdentityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

    }

    @Test
    void testSaveDailyDigestTimePreference() {
        // check regular parameter
        recordDefaultDailyDigestTimePreference();

        // check request without body
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void testMinuteFilter() {
        Header testMinIdentityHeader = TestHelpers.createRHIdentityHeader(testMinIdentityHeaderValue);

        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 00), Response.Status.OK.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 15), Response.Status.OK.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 30), Response.Status.OK.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 45), Response.Status.OK.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 10), Response.Status.BAD_REQUEST.getStatusCode());
        recordDailyDigestTimePreference(testMinIdentityHeader, LocalTime.of(5, 55), Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void testGetDailyDigestTimePreference() {
        LocalTime foundedPreference = given()
                .header(identityHeader)
                .when()
                .contentType(TEXT)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(LocalTime.class);
        assertEquals(LocalTime.MIDNIGHT, foundedPreference);

        recordDefaultDailyDigestTimePreference();

        foundedPreference = given()
                .header(identityHeader)
                .when()
                .contentType(TEXT)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .contentType(JSON)
                .extract().as(LocalTime.class);
        assertEquals(TIME, foundedPreference);
    }

    private void recordDefaultDailyDigestTimePreference() {
        recordDailyDigestTimePreference(identityHeader, TIME, Response.Status.OK.getStatusCode());
    }

    private void recordDailyDigestTimePreference(Header header, LocalTime time, int expectedReturnCode) {
        given()
                .header(header)
                .when()
                .contentType(JSON)
                .body(time)
                .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(expectedReturnCode);
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = initRbacMock("tenant", "orgId", "noAccess", NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock("tenant", "orgId", "readAccess", READ_ACCESS);

        given()
            .header(noAccessIdentityHeader)
            .when()
            .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(403);

        given()
            .header(noAccessIdentityHeader)
            .when()
            .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(403);

        given()
            .header(readAccessIdentityHeader)
            .when()
            .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(200);

        given()
            .header(readAccessIdentityHeader)
            .when()
            .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
            .then()
            .statusCode(403);
    }

    private Header initRbacMock(String accountId, String orgId, String username, MockServerConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }
}
