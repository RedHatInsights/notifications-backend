package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConsoleIdentityProviderTest {

    @Test
    void testNullOrgId() {
        Header identityHeader = buildIdentityHeader(null);
        given()
                .header(identityHeader)
                .when().get("/notifications/eventTypes")
                .then()
                .statusCode(401)
                .body(emptyString()); // We must NOT leak security impl details such as a missing field in the x-rh-identity header.
    }

    @Test
    void testEmptyOrgId() {
        Header identityHeader = buildIdentityHeader("");
        given()
                .header(identityHeader)
                .when().get("/notifications/eventTypes")
                .then()
                .statusCode(401)
                .body(emptyString()); // We must NOT leak security impl details such as a missing field in the x-rh-identity header.
    }

    @Test
    void testBlankOrgId() {
        Header identityHeader = buildIdentityHeader("   ");
        given()
                .header(identityHeader)
                .when().get("/notifications/eventTypes")
                .then()
                .statusCode(401)
                .body(emptyString()); // We must NOT leak security impl details such as a missing field in the x-rh-identity header.
    }

    @Test
    void testValidOrgId() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "123456", "johndoe");
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        Header identityHeader = buildIdentityHeader("123456");
        given()
            .header(identityHeader)
            .when().get(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications/eventTypes")
            .then()
            .statusCode(200);
    }

    @Test
    void testServiceAccountFullAccess() {
        testServiceAccount(MockServerConfig.RbacAccess.FULL_ACCESS, 200);
    }

    @Test
    void testServiceAccountNoAccess() {
        testServiceAccount(MockServerConfig.RbacAccess.NO_ACCESS, 403);
    }

    void testServiceAccount(MockServerConfig.RbacAccess mockServerConfig, int expectedHttpReturnCode) {
        String userId = UUID.randomUUID().toString();
        final String identityHeaderValue = TestHelpers.encodeRHServiceAccountIdentityInfo("123456", "johndoe", userId);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, mockServerConfig);

        Header identityHeader = buildServiceAccountIdentityHeader("123456", userId);
        given()
            .header(identityHeader)
            .when().get(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications/eventTypes")
            .then()
            .statusCode(expectedHttpReturnCode);
    }

    private static Header buildIdentityHeader(String orgId) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", orgId, "johndoe");
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    private static Header buildServiceAccountIdentityHeader(String orgId, String userId) {
        String identityHeaderValue = TestHelpers.encodeRHServiceAccountIdentityInfo(orgId, "johndoe", userId);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

}
