package com.redhat.cloud.notifications.routers.handlers.status;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
class StatusResourceTest {

    @InjectSpy
    BackendConfig backendConfig;

    public static final String STATUS_PAGE_URL = TestConstants.API_NOTIFICATIONS_V_1_0 + "/status";
    String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);

    @Test
    void testMaintenanceMode() {
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, READ_ACCESS);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        JsonObject response = new JsonObject(given()
            .header(identityHeader)
            .get(STATUS_PAGE_URL)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().body().asString());

        assertEquals("UP", response.getString("status"));

        when(backendConfig.isMaintenanceModeEnabled("GET_status")).thenReturn(true);
        response = new JsonObject(given()
            .header(identityHeader)
            .get(STATUS_PAGE_URL)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().body().asString());

        assertEquals("MAINTENANCE", response.getString("status"));
    }
}
