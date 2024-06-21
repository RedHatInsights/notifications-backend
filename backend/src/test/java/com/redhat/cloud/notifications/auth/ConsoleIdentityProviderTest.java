package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.principal.IllegalIdentityHeaderException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConsoleIdentityProviderTest {

    @Inject
    ConsoleIdentityProvider consoleIdentityProvider;

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

    /**
     * Tests that when a proper "x-rh-identity" header's value is given, a
     * Console principal is built.
     * @throws IllegalArgumentException in the case that the generated
     * "x-rh-identity" header's value is not valid.
     */
    @Test
    void testBuildPrincipalFromIdentityHeader() throws IllegalIdentityHeaderException {
        // Build a request with a proper "x-rh-identity" header's value.
        final String xRhIdentityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, TestConstants.DEFAULT_ORG_ID, "johndoe");
        final ConsoleAuthenticationRequest request = new ConsoleAuthenticationRequest(xRhIdentityHeaderValue);

        // Call the function under test.
        final Optional<Principal> principal = this.consoleIdentityProvider.buildPrincipalFromIdentityHeader(request);

        // Assert that the principal has been properly generated.
        Assertions.assertTrue(principal.isPresent(), "expecting a principal to be generated from a proper \"x-rh-identity\" header's value. It was not generated.");
    }

    /**
     * Tests that when a proper "x-rh-identity" header's value is not given,
     * then no principal is generated.
     * @throws IllegalArgumentException in the case that the generated
     * "x-rh-identity" header's value is not valid.
     */
    @Test
    void testBuildEmptyPrincipalFromIdentityHeader() throws IllegalIdentityHeaderException {
        // Build a request with no "x-rh-identity" header's value.
        final ConsoleAuthenticationRequest request = Mockito.mock(ConsoleAuthenticationRequest.class);
        Mockito.when(request.getAttribute(Constants.X_RH_IDENTITY_HEADER)).thenReturn(null);

        // Call the function under test.
        final Optional<Principal> principal = this.consoleIdentityProvider.buildPrincipalFromIdentityHeader(request);

        // Assert that no principal was generated.
        Assertions.assertFalse(principal.isPresent(), "expecting a principal to not be generated when no \"x-rh-identity\" header's value is present, but a principal was generated");
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
