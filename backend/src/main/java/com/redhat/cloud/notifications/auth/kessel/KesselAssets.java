package com.redhat.cloud.notifications.auth.kessel;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;
import org.project_kessel.api.inventory.v1beta1.CreateNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.CreateNotificationsIntegrationResponse;
import org.project_kessel.api.inventory.v1beta1.DeleteNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.DeleteNotificationsIntegrationResponse;
import org.project_kessel.api.inventory.v1beta1.Metadata;
import org.project_kessel.api.inventory.v1beta1.NotificationsIntegration;
import org.project_kessel.api.inventory.v1beta1.ReporterData;
import org.project_kessel.client.NotificationsIntegrationClient;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class KesselAssets {
    /**
     * Represents the timer's name for checking how much time it takes to
     * perform operations in Kessel for the "integration" resource.
     */
    private static final String KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME = "notifications.kessel.inventory.integration";

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
    public void createIntegration(final SecurityContext securityContext, final UUID workspaceId, final UUID integrationId) {
        // Build the request for Kessel's inventory.
        final CreateNotificationsIntegrationRequest request = this.buildCreateIntegrationRequest(securityContext, workspaceId, integrationId);

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Payload for the integration creation in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample createIntegrationTimer = Timer.start(this.meterRegistry);

        // Send the request to the inventory.
        final CreateNotificationsIntegrationResponse response;
        try {
            response = this.notificationsIntegrationClient.CreateNotificationsIntegration(request);
        } catch (final StatusRuntimeException e) {
            Log.errorf(
                e,
                "[identity: %s][workspace_id: %s][integration_id: %s] Unable to create integration in Kessel's inventory",
                SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request
            );

            throw e;
        }

        // Stop the timer.
        createIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.getKesselName())));

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
    public void deleteIntegration(final SecurityContext securityContext, final UUID workspaceId, final UUID integrationId) {
        // Build the request for Kessel's inventory.
        final DeleteNotificationsIntegrationRequest request = this.buildDeleteIntegrationRequest(securityContext, workspaceId, integrationId);

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Payload for deleting the integration in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample deleteIntegrationTimer = Timer.start(this.meterRegistry);

        // Send the request to the inventory.
        final DeleteNotificationsIntegrationResponse response;
        try {
            response = this.notificationsIntegrationClient.DeleteNotificationsIntegration(request);
        } catch (final StatusRuntimeException e) {
            Log.errorf(
                e,
                "[identity: %s][workspace_id: %s][integration_id: %s] Unable to delete integration in Kessel's inventory",
                SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, request
            );

            throw e;
        }

        // Stop the timer.
        deleteIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_INVENTORY_INTEGRATION_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.getKesselName())));

        Log.tracef("[identity: %s][workspace_id: %s][integration_id: %s] Received payload for the integration removal in Kessel's inventory: %s", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId, response);
        Log.debugf("[identity: %s][workspace_id: %s][integration_id: %s] Integration deleted in Kessel's inventory", SecurityContextUtil.extractRhIdentity(securityContext), workspaceId, integrationId);
    }

    /**
     * Builds a request to create an integration in the inventory.
     * @param securityContext the security context holding the identity.
     * @param workspaceId
     * @param integrationId
     * @return
     */
    protected CreateNotificationsIntegrationRequest buildCreateIntegrationRequest(final SecurityContext securityContext, final UUID workspaceId, final UUID integrationId) {
        final Timestamp reportedAt = Timestamps.fromMillis(Instant.now(Clock.systemUTC()).toEpochMilli());
        return CreateNotificationsIntegrationRequest.newBuilder()
            .setIntegration(
                NotificationsIntegration.newBuilder()
                    .setMetadata(Metadata.newBuilder()
                        .setFirstReported(reportedAt)
                        .setLastReported(reportedAt)
                        .setFirstReportedBy(SecurityContextUtil.getUsername(securityContext))
                        .setLastReportedBy(SecurityContextUtil.getUsername(securityContext))
                        .setResourceType("notifications-integration")
                        .setWorkspace(workspaceId.toString())
                        .build()
                    ).setReporterData(ReporterData.newBuilder()
                        .setReporterType(ReporterData.ReporterType.REPORTER_TYPE_OTHER)
                        .setFirstReported(reportedAt)
                        .setLastReported(reportedAt)
                        .setLocalResourceId(integrationId.toString())
                        .build()
                    ).build()
            ).build();
    }

    protected DeleteNotificationsIntegrationRequest buildDeleteIntegrationRequest(final SecurityContext securityContext, final UUID workspaceId, final UUID integrationId) {
        return DeleteNotificationsIntegrationRequest.newBuilder()
            // Resource format: \"<reporter_data.reporter_type>:<reporter_data.resourceId_alias>\
            .setResource(
                String.format(
                    "reporter_data.%s:reporter_data.%s",
                    ReporterData.ReporterType.REPORTER_TYPE_OTHER_VALUE,
                    integrationId
                )
            ).build();
    }
}
