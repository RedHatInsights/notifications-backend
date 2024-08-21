package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta1.CreateNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.DeleteNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.Metadata;
import org.project_kessel.api.inventory.v1beta1.ReporterData;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;

import java.util.UUID;

@QuarkusTest
public class KesselAssetsTest {
    @InjectMock
    BackendConfig backendConfig;

    @Inject
    KesselAssets kesselAssets;

    @InjectMock
    NotificationsIntegrationClient notificationsIntegrationClient;

    /**
     * Test that the function under test calls the Kessel inventory to create
     * the integration.
     */
    @Test
    void testCreateIntegration() {
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

        // Call the function under test.
        this.kesselAssets.createIntegration(mockedSecurityContext, UUID.randomUUID().toString(), UUID.randomUUID().toString());

        // Verify that the inventory call was made.
        Mockito.verify(this.notificationsIntegrationClient, Mockito.times(1)).CreateNotificationsIntegration(Mockito.any(CreateNotificationsIntegrationRequest.class));
    }

    /**
     * Test that the function under test calls the Kessel inventory to delete
     * the integration.
     */
    @Test
    void testDeleteIntegration() {
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

        // Call the function under test.
        this.kesselAssets.deleteIntegration(mockedSecurityContext, UUID.randomUUID().toString(), UUID.randomUUID().toString());

        // Verify that the inventory call was made.
        Mockito.verify(this.notificationsIntegrationClient, Mockito.times(1)).DeleteNotificationsIntegration(Mockito.any(DeleteNotificationsIntegrationRequest.class));
    }

    /**
     * Tests that the request for creating an integration in Kessel's inventory
     * is properly built.
     */
    @Test
    void testBuildCreateIntegrationRequest() {
        // Build the request.
        final String workspaceId = UUID.randomUUID().toString();
        final String integrationId = UUID.randomUUID().toString();

        final CreateNotificationsIntegrationRequest request = this.kesselAssets.buildCreateIntegrationRequest(workspaceId, integrationId);

        // Assert that the request was properly built.
        final Metadata metadata = request.getIntegration().getMetadata();
        Assertions.assertEquals(ResourceType.INTEGRATION.getKesselName(), metadata.getResourceType(), "the \"resource type\" should have been an integration");
        Assertions.assertEquals(workspaceId, metadata.getWorkspace(), "the workspace ID was incorrectly set");

        final ReporterData reporterData = request.getIntegration().getReporterData();
        Assertions.assertEquals(ReporterData.ReporterType.REPORTER_TYPE_OTHER, reporterData.getReporterType(), "the \"reporter type\" should be other");
        Assertions.assertEquals(integrationId, reporterData.getLocalResourceId(), "the \"local resource id\" was incorrectly set");
    }

    /**
     * Tests that the request for deleting an integration from the Kessel's
     * inventory is properly built.
     */
    @Test
    void testBuildDeleteIntegrationRequest() {
        // Build the request.
        final String integrationId = UUID.randomUUID().toString();

        final DeleteNotificationsIntegrationRequest request = this.kesselAssets.buildDeleteIntegrationRequest(integrationId);

        // Assert that the request was properly built.
        final String expectedResource = String.format(KesselAssets.RESOURCE_FORMAT, ReporterData.ReporterType.REPORTER_TYPE_OTHER_VALUE, integrationId);

        Assertions.assertEquals(expectedResource, request.getResource(), "unexpected resource format created for the request.");
    }
}
