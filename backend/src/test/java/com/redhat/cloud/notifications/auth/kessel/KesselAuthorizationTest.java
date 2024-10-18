package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhServiceAccountIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhUserIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.relations.v1beta1.CheckRequest;
import org.project_kessel.api.relations.v1beta1.CheckResponse;
import org.project_kessel.api.relations.v1beta1.LookupResourcesRequest;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.SubjectReference;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;

@QuarkusTest
public class KesselAuthorizationTest {
    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    CheckClient checkClient;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    LookupClient lookupClient;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    KesselTestHelper kesselTestHelper;

    /**
     * Enable the Kessel's Relations back end for every test.
     */
    @BeforeEach
    public void enableKesselRelations() {
        Mockito.when(this.backendConfig.isKesselRelationsEnabled()).thenReturn(true);
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * does not raise an exception.
     */
    @Test
    void testAuthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a positive response.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_LOG_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        // Call the function under test.
        this.kesselAuthorization.hasPermissionOnResource(
            mockedSecurityContext,
            WorkspacePermission.EVENT_LOG_VIEW,
            ResourceType.WORKSPACE,
            WORKSPACE_ID_PLACEHOLDER
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());
    }

    /**
     * Tests that when the principal is authorized, the function under test
     * throws an exception.
     */
    @Test
    void testUnauthorized() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a negative response.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.EVENT_LOG_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER, CheckResponse.Allowed.ALLOWED_FALSE);

        // Call the function under test and expect that it throws a "Forbidden"
        // exception.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.hasPermissionOnResource(
                mockedSecurityContext,
                WorkspacePermission.EVENT_LOG_VIEW,
                ResourceType.WORKSPACE,
                WORKSPACE_ID_PLACEHOLDER
            ),
            "unexpected exception thrown, as with a negative response from Kessel it should throw a \"Forbidden exception\""
        );

        // Verify that we called Kessel.
        Mockito.verify(this.checkClient, Mockito.times(1)).check(Mockito.any());
    }

    /**
     * Tests that the incoming integration UUID's are correctly parsed and
     * returned in a set.
     */
    @Test
    void testLookupAuthorizedIntegrations() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a few resource IDs in the response.
        final UUID firstUuid = UUID.randomUUID();
        final UUID secondUuid = UUID.randomUUID();
        final UUID thirdUuid = UUID.randomUUID();

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(firstUuid, secondUuid, thirdUuid));

        // Call the function under test.
        final Set<UUID> result = this.kesselAuthorization.lookupAuthorizedIntegrations(mockedSecurityContext, IntegrationPermission.VIEW);

        // Assert that the result is the expected one.
        final Set<UUID> expectedUuids = Set.of(firstUuid, secondUuid, thirdUuid);

        result.forEach(r -> Assertions.assertTrue(expectedUuids.contains(r), String.format("UUID \"%s\" not present in the expected UUIDs", r)));
    }

    /**
     * Test that when the principal is authorized to perform the action, and
     * that the integration exists, then no exception gets thrown.
     */
    @Test
    void testPrincipalIsAuthorizedAndIntegrationExists() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a positive response.
        final UUID integrationId = UUID.randomUUID();
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString());

        // Call the function under test
        this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(mockedSecurityContext, IntegrationPermission.VIEW, integrationId);
    }

    /**
     * Tests that when the principal is unauthorized but when the integration
     * exists, a {@link ForbiddenException} is thrown.
     */
    @Test
    void testPrincipalIsUnauthorizedAndIntegrationExists() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a negative response.
        final UUID integrationId = UUID.randomUUID();
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString(), CheckResponse.Allowed.ALLOWED_FALSE);

        // Simulate that the integration exists.
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(integrationId, DEFAULT_ORG_ID)).thenReturn(true);

        // Call the function under test.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(mockedSecurityContext, IntegrationPermission.VIEW, integrationId)
        );
    }

    /**
     * Tests that when the principal is unauthorized, the integration does
     * not exist, and the principal does not have the workspace permission, a
     * {@link ForbiddenException} is thrown.
     */
    @Test
    void testPrincipalIsUnauthorizedAndIntegrationNotExistsAndNotWorkspacePermission() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Simulate that Kessel returns a negative response.
        final UUID integrationId = UUID.randomUUID();
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString(), CheckResponse.Allowed.ALLOWED_FALSE);

        // Simulate that the integration does not exist.
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(integrationId, DEFAULT_ORG_ID)).thenReturn(false);

        // Simulate that Kessel returns a negative response for the workspace
        // permission.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER, CheckResponse.Allowed.ALLOWED_FALSE);

        // Call the function under test.
        Assertions.assertThrows(
            ForbiddenException.class,
            () -> this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(mockedSecurityContext, IntegrationPermission.VIEW, integrationId)
        );
    }

    /**
     * Tests that when the principal is unauthorized, the integration does
     * not exist, and the principal has the workspace permission, a
     * {@link jakarta.ws.rs.NotFoundException} is thrown.
     */
    @Test
    void testPrincipalIsUnauthorizedAndIntegrationNotExistsAndWorkspacePermission() {
        // Mock the security context.
        final SecurityContext mockedSecurityContext = this.mockSecurityContext();

        // Enable the Kessel back end integration for this test.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled()).thenReturn(true);

        // Simulate that Kessel returns a negative response.
        final UUID integrationId = UUID.randomUUID();
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.VIEW, ResourceType.INTEGRATION, integrationId.toString(), CheckResponse.Allowed.ALLOWED_FALSE);

        // Simulate that the integration does not exist.
        Mockito.when(this.endpointRepository.existsByUuidAndOrgId(integrationId, DEFAULT_ORG_ID)).thenReturn(false);

        // Simulate that Kessel returns a positive response for the workspace
        // permission.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        // Call the function under test.
        final NotFoundException e = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(mockedSecurityContext, IntegrationPermission.VIEW, integrationId)
        );

        // Assert that the exception message is the expected one.
        final JsonObject expectedPayload = new JsonObject();
        expectedPayload.put("error", "Integration not found");

        Assertions.assertEquals(expectedPayload.encode(), e.getMessage(), "unexpected exception message received");
    }

    /**
     * Test that permission check requests are properly built for both service
     * accounts and users.
     */
    @Test
    void testBuildCheckRequest() {
        record TestCase(RhIdentity identity, ResourceType resourceType, KesselPermission permission, String resourceId) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "identity='" + this.identity + '\'' +
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
            new TestCase(userIdentity, ResourceType.INTEGRATION, IntegrationPermission.VIEW, "12345"),
            new TestCase(serviceAccountIdentity, ResourceType.INTEGRATION, IntegrationPermission.EDIT, "54321"),
            new TestCase(userIdentity, ResourceType.WORKSPACE, WorkspacePermission.CREATE_DRAWER_INTEGRATION, "workspace-a"),
            new TestCase(serviceAccountIdentity, ResourceType.WORKSPACE, WorkspacePermission.EVENT_LOG_VIEW, "workspace-b")
        );

        for (final TestCase tc : testCases) {
            // Call the function under test.
            final CheckRequest checkRequest = this.kesselAuthorization.buildCheckRequest(tc.identity(), tc.permission(), tc.resourceType(), tc.resourceId());

            // Make sure the request was built appropriately.
            final ObjectReference objectReference = checkRequest.getResource();
            Assertions.assertEquals(tc.resourceType().getKesselObjectType(), objectReference.getType(), String.format("unexpected resource type obtained for the object's reference on test case: %s", tc));
            Assertions.assertEquals(tc.resourceId(), objectReference.getId(), String.format("unexpected resource ID obtained for the object's reference on test case: %s", tc));

            Assertions.assertEquals(tc.permission().getKesselPermissionName(), checkRequest.getRelation(), String.format("unexpected relation obtained on test case: %s", tc));

            final SubjectReference subjectReference = checkRequest.getSubject();
            Assertions.assertEquals(KesselAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE, subjectReference.getSubject().getType().getName(), String.format("unexpected resource type obtained for the subject's reference on test case: %s", tc));
            Assertions.assertEquals(tc.identity().getName(), subjectReference.getSubject().getId(), String.format("unexpected resource ID obtained for the subject's reference on test case: %s", tc));
        }
    }

    /**
     * Tests that the "lookup resources" requests are properly built both for
     * service accounts and users.
     */
    @Test
    void testBuildLookupResourcesRequest() {
        record TestCase(RhIdentity identity, KesselPermission permission) {
            @Override
            public String toString() {
                return "TestCase{" +
                    "identity='" + this.identity + '\'' +
                    ", kesselPermission='" + this.permission + '\'' +
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
            new TestCase(userIdentity, IntegrationPermission.VIEW),
            new TestCase(serviceAccountIdentity, IntegrationPermission.VIEW)
        );

        for (final TestCase tc : testCases) {
            // Call the function under test.
            final LookupResourcesRequest lookupResourcesRequest = this.kesselAuthorization.buildLookupResourcesRequest(tc.identity(), tc.permission());

            // Make sure the request was built appropriately.
            final SubjectReference subjectReference = lookupResourcesRequest.getSubject();
            Assertions.assertEquals(KesselAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE, subjectReference.getSubject().getType().getName(), String.format("unexpected resource type obtained for the subject's reference on test case: %s", tc));
            Assertions.assertEquals(tc.identity().getName(), subjectReference.getSubject().getId(), String.format("unexpected resource ID obtained for the subject's reference on test case: %s", tc));

            Assertions.assertEquals(tc.permission().getKesselPermissionName(), lookupResourcesRequest.getRelation(), String.format("unexpected relation obtained on test case: %s", tc));

            Assertions.assertEquals(ResourceType.INTEGRATION.getKesselObjectType(), lookupResourcesRequest.getResourceType(), String.format("unexpected resource type obtained on test case: %s", tc));
        }
    }

    /**
     * Mocks a {@link SecurityContext} with a {@link RhIdentity} identity
     * principal inside of it. The username used for the principal is the
     * {@link com.redhat.cloud.notifications.TestConstants#DEFAULT_USER}
     * @return the built security context.
     */
    private SecurityContext mockSecurityContext() {
        final SecurityContext mockedSecurityContext = Mockito.mock(SecurityContext.class);

        // Create a RhIdentity principal and assign it to the mocked security
        // context.
        final RhIdentity identity = Mockito.mock(RhIdentity.class);
        Mockito.when(identity.getName()).thenReturn(DEFAULT_USER);
        Mockito.when(identity.getOrgId()).thenReturn(DEFAULT_ORG_ID);

        final ConsolePrincipal<?> principal = new RhIdPrincipal(identity);
        Mockito.when(mockedSecurityContext.getUserPrincipal()).thenReturn(principal);

        return mockedSecurityContext;
    }
}
