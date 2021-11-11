package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.FULL_ACCESS;
import static io.restassured.RestAssured.given;

@QuarkusTest
class ValidationEndpointTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void shouldReturnNotFoundWhenTripleIsInvalid() {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo("empty", "user");
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
                .header(identityHeader)
                .param("bundle", "1")
                .param("application", "2")
                .param("eventtype", "3")
                .when()
                .get("/validation")
                .then()
                .statusCode(404);
    }
}
