package com.redhat.cloud.notifications.auth.kessel;

import api.check.v1.CheckRequest;
import api.check.v1.CheckResponse;
import api.relations.v1.ObjectReference;
import api.relations.v1.SubjectReference;
import client.CheckClient;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhServiceAccountIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhUserIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeSamlIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@QuarkusTest
public class KesselAuthorizationTest {
    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    CheckClient checkClient;

    @Inject
    KesselAuthorization kesselAuthorization;

    /**
     * Tests that when the principal is authorized, the function under test
     * does not raise an exception.
     */
    @Disabled
    @Test
    void testAuthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselBackendEnabled()).thenReturn(true);

        // Simulate that Kessel returns a positive response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_TRUE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test.
        this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            WorkspacePermission.INTEGRATIONS_READ,
            ResourceType.WORKSPACE,
            "workspace-uuid"
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * throws an exception.
     */
    @Disabled
    @Test
    void testUnauthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselBackendEnabled()).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final CheckResponse positiveCheckResponse = CheckResponse.newBuilder().setAllowed(CheckResponse.Allowed.ALLOWED_FALSE).build();
        Mockito.when(this.checkClient.check(Mockito.any())).thenReturn(positiveCheckResponse);

        // Call the function under test and expect that it throws a "Forbidden"
        // exception.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                mockedSecurityContext,
                WorkspacePermission.INTEGRATIONS_READ,
                ResourceType.WORKSPACE,
                "workspace-uuid"
            ),
            "unexpected exception thrown, as with a negative response from Kessel it should throw a \"Forbidden exception\""
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());
    }

    /**
     * Test that permission check requests are properly built for both service
     * accounts and users.
     */
    @Test
    void testBuildCheckRequest() {
        record TestCase(RhIdentity identity, String expectedIdentityType, ResourceType resourceType, KesselPermission permission, String resourceId) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "identity='" + this.identity + '\'' +
                    ", expectedIdentityType='" + this.expectedIdentityType + '\'' +
                    ", resourceType='" + this.resourceType + '\'' +
                    ", kesselPermission='" + this.permission + '\'' +
                    ", resourceId='" + this.resourceId + '\'' +
                    '}';
            }
        }

        // Create a user identity object.
        final String username = "Red Hat user";
        final RhIdentity userIdentity = Mockito.mock(RhUserIdentity.class);
        Mockito.when(userIdentity.getName()).thenReturn(username);

        // Create a service account identity object.
        final String serviceAccountName = String.format("service-account-%s", UUID.randomUUID());
        final RhIdentity serviceAccountIdentity = Mockito.mock(RhServiceAccountIdentity.class);
        Mockito.when(serviceAccountIdentity.getName()).thenReturn(serviceAccountName);

        // Loop through the supported identities.
        final List<TestCase> testCases = List.of(
            new TestCase(userIdentity, KesselAuthorization.KESSEL_IDENTITY_SUBJECT_USER, ResourceType.ENDPOINT, ResourcePermission.VIEW, "12345"),
            new TestCase(serviceAccountIdentity, KesselAuthorization.KESSEL_IDENTITY_SUBJECT_SERVICE_ACCOUNT, ResourceType.ENDPOINT, ResourcePermission.WRITE, "54321"),
            new TestCase(userIdentity, KesselAuthorization.KESSEL_IDENTITY_SUBJECT_USER, ResourceType.WORKSPACE, WorkspacePermission.INTEGRATIONS_READ, "workspace-a"),
            new TestCase(serviceAccountIdentity, KesselAuthorization.KESSEL_IDENTITY_SUBJECT_SERVICE_ACCOUNT, ResourceType.WORKSPACE, WorkspacePermission.EVENTS_READ, "workspace-b")
        );

        for (final TestCase tc : testCases) {
            // Call the function under test.
            final CheckRequest checkRequest = this.kesselAuthorization.buildCheckRequest(tc.identity(), tc.permission(), tc.resourceType(), tc.resourceId());

            // Make sure the request was built appropriately.
            final ObjectReference objectReference = checkRequest.getObject();
            Assertions.assertEquals(tc.resourceType().getKesselName(), objectReference.getType(), String.format("unexpected resource type obtained for the object's reference on test case: %s", tc));
            Assertions.assertEquals(tc.resourceId(), objectReference.getId(), String.format("unexpected resource ID obtained for the object's reference on test case: %s", tc));
            Assertions.assertEquals(tc.resourceId(), objectReference.getId(), String.format("unexpected resource ID obtained for the object's reference on test case: %s", tc));

            Assertions.assertEquals(tc.permission().getKesselPermissionName(), checkRequest.getRelation(), String.format("unexpected relation obtained on test case: %s", tc));

            final SubjectReference subjectReference = checkRequest.getSubject();
            Assertions.assertEquals(tc.expectedIdentityType(), subjectReference.getObject().getType(), String.format("unexpected resource type obtained for the subject's reference on test case: %s", tc));
            Assertions.assertEquals(tc.identity().getName(), subjectReference.getObject().getId(), String.format("unexpected resource ID obtained for the subject's reference on test case: %s", tc));
        }
    }

    /**
     * Test that the {@link RhIdentity} is correctly extracted from a security
     * context.
     */
    @Test
    void testExtractRhIdentity() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn("Red Hat user");

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        // Call the function under test.
        final RhIdentity result = this.kesselAuthorization.extractRhIdentity(mockedSecurityContext);

        // Assert that the objects are the same. Just by checking the object's
        // reference we can be sure that our stubbed principal above is the
        // one that was extracted.
        Assertions.assertEquals(
            identity,
            result,
            "the extracted identity object was not the same"
        );
    }

    /**
     * Test that when a "non-console" principal is extracted from the security
     * context, an exception is raised.
     */
    @Test
    void testExtractRhIdentityNoConsolePrincipalThrowsException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Mock a generic principal and make the context return it when asked
        // for it.
        final Principal mockedPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(Mockito.mock(Principal.class));

        // Call the function under test.
        final IllegalStateException e = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.kesselAuthorization.extractRhIdentity(mockedSecurityContext)
        );

        // Assert that the correct exception has been thrown.
        Assertions.assertEquals(
            String.format("unable to extract RH Identity object from principal. Expected \"Console Principal\" object type, got \"%s\"", mockedPrincipal.getClass().getName()),
            e.getMessage(),
            "unexpected exception message"
        );
    }

    /**
     * Test that a "non-RhIdentity" identity inside a principal raises an
     * exception.
     */
    @Test
    void testExtractRhIdentityNoSupportedIdentityThrowsException() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Mock an unexpected identity which should trigger an exception.
        final TurnpikeSamlIdentity turnpikeSamlIdentity = new TurnpikeSamlIdentity();
        turnpikeSamlIdentity.associate = new TurnpikeSamlIdentity.Associate();
        turnpikeSamlIdentity.associate.email = "example@redhat.com";
        turnpikeSamlIdentity.type = "turnpike";

        // Make the identity part of the principal.
        final ConsolePrincipal<?> turnpikePrincipal = new TurnpikePrincipal(turnpikeSamlIdentity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(turnpikePrincipal);

        // Call the function under test.
        final IllegalStateException e = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.kesselAuthorization.extractRhIdentity(mockedSecurityContext)
        );

        // Assert that the correct exception has been thrown.
        Assertions.assertEquals(
            String.format("unable to extract RH Identity object from principal. Expected \"RhIdentity\" object type, got \"%s\"", turnpikeSamlIdentity.getClass().getName()),
            e.getMessage(),
            "unexpected exception message"
        );
    }
}
