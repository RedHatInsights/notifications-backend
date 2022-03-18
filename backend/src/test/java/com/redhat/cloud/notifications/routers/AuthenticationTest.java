package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class AuthenticationTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @CacheName("rbac-cache")
    Cache cache;

    @Test
    void testEndpointRoles() {
        String tenant = "empty";
        String userName = "testEndpointRoles";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(tenant, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        // Fetch endpoint without any Rbac details - errors cause 401 -- unauthorized
        given()
                // Don't set the header at all
                .when().get("/endpoints")
                .then()
                .statusCode(401);

        cache.invalidateAll().await().indefinitely();

        // Fetch endpoint without any Rbac details - errors cause 401
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(401);

        cache.invalidateAll().await().indefinitely();

        // Fetch endpoint with no access - Rbac succeed returns 403
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.NO_ACCESS);

        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(403);

        cache.invalidateAll().await().indefinitely();

        // Test bogus x-rh-identity header that fails Base64 decoding
        given()
                .header(new Header("x-rh-identity", "00000"))
                .when().get("/endpoints")
                .then()
                .statusCode(401);
    }
}
