package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.principal.IllegalIdentityHeaderException;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_USER;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConsoleIdentityProviderTest {

    @Inject
    ConsoleIdentityProvider consoleIdentityProvider;

    @InjectMock
    BackendConfig backendConfig;

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

    /**
     * Test that when Kessel Relations is enabled, and an incoming request
     * has a {@link com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeIdentity},
     * the built principal has
     * Regression test for <a href="https://issues.redhat.com/browse/RHCLOUD-38438">RHCLOUD-38438</a>.
     * Essentially the
     */
    @Test
    void testTurnpikePrincipalGetsRolesWhenKesselEnabled() {
        // Enable Kessel for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled(Mockito.anyString())).thenReturn(true);

        record TestCase(String[] turnpikeRoles) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "turnpikeRoles=" + Arrays.toString(turnpikeRoles) +
                    '}';
            }
        }

        final List<TestCase> testCases = List.of(
            new TestCase(new String[]{}),
            new TestCase(new String[]{"custom-role"}),
            new TestCase(new String[]{RBAC_INTERNAL_ADMIN}),
            new TestCase(new String[]{RBAC_INTERNAL_ADMIN, "custom-role", "custom-role-two"})
        );

        for (final TestCase tc : testCases) {
            // Build a request with a Turnpike identity inside the "x-rh-identity"
            // header.
            final ConsoleAuthenticationRequest request = new ConsoleAuthenticationRequest(
                TestHelpers.encodeTurnpikeIdentityInfo(DEFAULT_USER, tc.turnpikeRoles())
            );

            // Call the function under test.
            final SecurityIdentity securityIdentity = this.consoleIdentityProvider
                .authenticate(request, Mockito.mock(AuthenticationRequestContext.class))
                .await()
                .indefinitely();

            // Assert that a Turnpike principal was built.
            Assertions.assertInstanceOf(TurnpikePrincipal.class, securityIdentity.getPrincipal(), "the function under test did not generate a Turnpike principal from an \"x-rh-identity\" header which contained a Turnpike identity");

            // Assert that the "internal user" role is set, as it should for
            // every Turnpike authenticated request.
            Assertions.assertTrue(securityIdentity.hasRole(RBAC_INTERNAL_USER), String.format("every Turnpike authenticated request must have the \"read:internal\" role assigned, but the built security identity did not have it for test case: %s. Security identity roles: %s", tc, securityIdentity.getRoles()));

            // Asser that the rest of the roles are present.
            for (final String tcTurnpikeRole : tc.turnpikeRoles()) {
                // The security identity transforms the Turnpike roles using a
                // particular format, so we need to adjust the expectations.
                final String expectedRole = InternalRoleAccess.getInternalRole(tcTurnpikeRole);

                Assertions.assertTrue(securityIdentity.getRoles().contains(expectedRole), String.format("role \"%s\" not found in the built security identity, although the test case demanded it to be there. Test case: %s. Security identity roles: %s", expectedRole, tc, securityIdentity.getRoles()));
            }
        }
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
