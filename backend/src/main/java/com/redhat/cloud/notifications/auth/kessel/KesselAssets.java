package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;
import org.project_kessel.api.inventory.v1beta1.resources.CreateNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.resources.CreateNotificationsIntegrationResponse;
import org.project_kessel.api.inventory.v1beta1.resources.DeleteNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.resources.DeleteNotificationsIntegrationResponse;
import org.project_kessel.api.inventory.v1beta1.resources.Metadata;
import org.project_kessel.api.inventory.v1beta1.resources.NotificationsIntegration;
import org.project_kessel.api.inventory.v1beta1.resources.ReporterData;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;

@ApplicationScoped
public class KesselAssets {
    /**
     * Represents the timer's name for checking how much time it takes to
     * perform operations in Kessel for the "integration" resource.
     */
    private static final String KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME = "notifications.kessel.inventory.resources";

    @Inject
    BackendConfig backendConfig;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    NotificationsIntegrationClient notificationsIntegrationClient;

    /**
     * Creates an integration in Kessel's inventory.
     * @param securityContext the security context which holds the subject of
     *                        the operation.
     * @param workspaceId the identifier for the workspace the integration will
     *                    be created on.
     * @param integrationId the identifier of the integration we created in
     *                      our side.
     */
    public void createIntegration(final SecurityContext securityContext, final String workspaceId, final String integrationId) {
        // Build the request for Kessel's inventory.
        final CreateNotificationsIntegrationRequest request = this.buildCreateIntegrationRequest(workspaceId, integrationId);

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Payload for the integration creation in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample createIntegrationTimer = Timer.start(this.meterRegistry);

        // Send the request to the inventory.
        final CreateNotificationsIntegrationResponse response;
        try {
            response = this.notificationsIntegrationClient.CreateNotificationsIntegration(request);
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][workspace_id: %s][integration_id: %s] Unable to create integration in Kessel's inventory",
                SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request
            );

            throw e;
        }

        // Stop the timer.
        createIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.name())));

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Received payload for the integration creation in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, response);
        Log.debugf("[identity: %s][workspace_id: %s][integration_id: %s] Integration created in Kessel's inventory", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId);
    }

    /**
     * Deletes an integration from Kessel's inventory.
     * @param securityContext the security context which holds the subject of
     *                        the operation.
     * @param workspaceId the identifier for the workspace the integration will
     *                    be deleted from.
     * @param integrationId the identifier of the integration we deleted on
     *                      our side.
     */
    public void deleteIntegration(final SecurityContext securityContext, final String workspaceId, final String integrationId) {
        // Build the request for Kessel's inventory.
        final DeleteNotificationsIntegrationRequest request = this.buildDeleteIntegrationRequest(integrationId);

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Payload for deleting the integration in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample deleteIntegrationTimer = Timer.start(this.meterRegistry);

        // Send the request to the inventory.
        final DeleteNotificationsIntegrationResponse response;
        try {
            response = this.notificationsIntegrationClient.DeleteNotificationsIntegration(request);
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][workspace_id: %s][integration_id: %s] Unable to delete integration in Kessel's inventory",
                SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request
            );

            throw e;
        }

        // Stop the timer.
        deleteIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.name())));

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Received payload for the integration removal in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, response);
        Log.debugf("[identity: %s][workspace_id: %s][integration_id: %s] Integration deleted in Kessel's inventory", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId);
    }

    /**
     * Builds a request to create an integration in the inventory.
     * @param workspaceId the identifier of the workspace the integration will
     *                    be created on.
     * @param integrationId the identifier of the integration that we created
     *                      on our database.
     * @return the creation request ready to be sent.
     */
    protected CreateNotificationsIntegrationRequest buildCreateIntegrationRequest(final String workspaceId, final String integrationId) {
        return CreateNotificationsIntegrationRequest.newBuilder()
            .setIntegration(
                NotificationsIntegration.newBuilder()
                    .setMetadata(Metadata.newBuilder()
                        .setResourceType(ResourceType.INTEGRATION.getKesselRepresentation())
                        .setWorkspace(workspaceId)
                        .build()
                    ).setReporterData(ReporterData.newBuilder()
                        .setLocalResourceId(integrationId)
                        .setReporterInstanceId(this.backendConfig.getKesselInventoryReporterInstanceId())
                        .setReporterType(ReporterData.ReporterType.NOTIFICATIONS)
                        .build()
                    ).build()
            ).build();
    }

    /**
     * Deletes an integration from Kessel's inventory.
     * @param integrationId the identifier of the integration we deleted from
     *                      our database.
     * @return the deletion request ready to be sent.
     */
    protected DeleteNotificationsIntegrationRequest buildDeleteIntegrationRequest(final String integrationId) {
        return DeleteNotificationsIntegrationRequest.newBuilder()
            .setReporterData(
                ReporterData.newBuilder()
                    .setLocalResourceId(integrationId)
                    .setReporterInstanceId(this.backendConfig.getKesselInventoryReporterInstanceId())
                    .setReporterType(ReporterData.ReporterType.NOTIFICATIONS)
            ).build();
    }
}
