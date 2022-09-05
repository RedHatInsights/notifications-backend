package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

@QuarkusTest
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

    private static Header buildIdentityHeader(String orgId) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", orgId, "johndoe");
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }
}
