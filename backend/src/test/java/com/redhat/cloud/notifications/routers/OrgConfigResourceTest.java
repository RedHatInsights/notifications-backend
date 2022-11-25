package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.routers.models.DailyDigestTimeSettings;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class OrgConfigResourceTest extends DbIsolatedTest {

    static final String TIME = "10:10:00";
    public static final String ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL = "/org-config/notification-daily-digest-time-preference";
    String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("empty", "empty", "user");
    Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
    }

    @Test
    void testSaveDailyDigestTimePreference() {
        // check regular parameter
        recordDefaultDailyDigestTimePreference();
        // check request with wrong parameter
        recordDailyDigestTimePreference(null, Response.Status.BAD_REQUEST.getStatusCode());
        // check request with wrong parameter
        recordDailyDigestTimePreference("abc", Response.Status.BAD_REQUEST.getStatusCode());
        // check request with wrong parameter
        recordDailyDigestTimePreference("25:00:00", Response.Status.BAD_REQUEST.getStatusCode());
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
    void testGetDailyDigestTimePreference() {

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        recordDefaultDailyDigestTimePreference();

        DailyDigestTimeSettings foundedPreference = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .get(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode()).extract().as(DailyDigestTimeSettings.class);
        assertEquals(TIME, foundedPreference.dailyDigestTimePreference);
    }

    private void deleteDailyDigestTimePreference() {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .delete(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    private void recordDefaultDailyDigestTimePreference() {
        recordDailyDigestTimePreference(TIME, Response.Status.OK.getStatusCode());
    }

    private void recordDailyDigestTimePreference(String time, int expectedReturnCode) {
        DailyDigestTimeSettings dayDailyDigestTimeSettings = new DailyDigestTimeSettings();
        dayDailyDigestTimeSettings.dailyDigestTimePreference = time;

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(dayDailyDigestTimeSettings)
                .put(ORG_CONFIG_NOTIFICATION_DAILY_DIGEST_TIME_PREFERENCE_URL)
                .then()
                .statusCode(expectedReturnCode);
    }

}
