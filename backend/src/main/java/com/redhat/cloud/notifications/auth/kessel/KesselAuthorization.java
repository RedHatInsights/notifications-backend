package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhServiceAccountIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.project_kessel.api.relations.v1beta1.CheckRequest;
import org.project_kessel.api.relations.v1beta1.CheckResponse;
import org.project_kessel.api.relations.v1beta1.LookupResourcesRequest;
import org.project_kessel.api.relations.v1beta1.LookupResourcesResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.api.relations.v1beta1.SubjectReference;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class KesselAuthorization {
    /**
     * Represents the "service account"'s subject's name in Kessel.
     */
    public static final String KESSEL_IDENTITY_SUBJECT_SERVICE_ACCOUNT = "service-account";
    /**
     * Represents the "user"'s subject's name in Kessel.
     */
    public static final String KESSEL_IDENTITY_SUBJECT_USER = "user";
    /**
     * Represents the key for the "permission" tag used in the timer.
     */
    private static final String KESSEL_METRICS_TAG_PERMISSION_KEY = "permission";
    /**
     * Represents the timer's name to measure the time spent looking up for
     * authorized resources for a particular subject.
     */
    private static final String KESSEL_METRICS_LOOKUP_RESOURCES_TIMER_NAME = "notifications.kessel.relationships.lookup.resources.requests";
    /**
     * Represents the timer's name to measure the time spent checking for a
     * particular permission for a subject.
     */
    private static final String KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME = "notifications.kessel.relationships.permission.check.requests";

    @Inject
    BackendConfig backendConfig;

    @Inject
    CheckClient checkClient;

    @Inject
    LookupClient lookupClient;

    @Inject
    MeterRegistry meterRegistry;

    /**
     * Checks if the subject on the security context has permission on the
     * given resource. Throws
     * @param securityContext the security context to extract the subject from.
     * @param permission the permission we want to check.
     * @param resourceType the resource type we should check the permission
     *                     against.
     * @param resourceId the identifier of the resource.
     * @throws ForbiddenException in case of not being authorized.
     */
    public void hasPermissionOnResource(final SecurityContext securityContext, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        // Skip the authorization step with Kessel if it is not enabled.
        if (!this.backendConfig.isKesselBackendEnabled()) {
            return;
        }

        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        // Build the request for Kessel.
        final CheckRequest permissionCheckRequest = this.buildCheckRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckResponse response;
        try {
            response = this.checkClient.check(permissionCheckRequest);
        } catch (final StatusRuntimeException e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Runtime error when querying Kessel for a permission check with request payload: %s",
                identity, permission, resourceType, resourceId, permissionCheckRequest
            );

            throw e;
        }

        // Stop the timer.
        permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.getKesselName())));

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (CheckResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            throw new ForbiddenException();
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
    }

    /**
     * Looks up the integrations the security context's subject has the given
     * permission for.
     * @param securityContext the security context holding the subject's
     *                        identity.
     * @param integrationPermission the integration's permission we want to use
     *                              to filter the target integrations with.
     * @return a set of integration IDs the user has permission to access.
     */
    public Set<UUID> lookupAuthorizedIntegrations(final SecurityContext securityContext, final IntegrationPermission integrationPermission) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        // Build the lookup request for Kessel.
        final LookupResourcesRequest request = this.buildLookupResourcesRequest(identity, integrationPermission);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s] Payload for the resource lookup: %s", identity, integrationPermission, ResourceType.INTEGRATION, request);

        // Measure the time it takes to perform the lookup operation.
        final Timer.Sample lookupTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final Iterator<LookupResourcesResponse> responses;
        try {
            responses = this.lookupClient.lookupResources(request);
        } catch (final StatusRuntimeException e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s] Runtime error when querying Kessel for integration resources with request payload: %s",
                identity, integrationPermission, ResourceType.INTEGRATION, request
            );

            throw e;
        }

        // Stop the timer.
        lookupTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_LOOKUP_RESOURCES_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, integrationPermission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.getKesselName())));

        // Process the incoming responses.
        final Set<UUID> uuids = new HashSet<>();
        while (responses.hasNext()) {
            final LookupResourcesResponse response = responses.next();

            Log.tracef("[identity: %s][permission: %s][resource_type: %s] Received payload for the resource lookup: %s", identity, integrationPermission, ResourceType.INTEGRATION, response);

            uuids.add(UUID.fromString(response.getResource().getId()));
        }

        return uuids;
    }

    /**
     * Build a check request for a particular resource, to see if the subject
     * of the identity has permission on it.
     * @param identity the subject's identity.
     * @param permission the permission we want to check for the given subject
     *                   and resource.
     * @param resourceType the resource type we are attempting to verify.
     * @param resourceId the resource's identifier.
     * @return the built check request for Kessel ready to be sent.
     */
    protected CheckRequest buildCheckRequest(final RhIdentity identity, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        // Extract the subject's type from the subject's identity.
        final String type = this.extractSubjectTypeFromRhIdentity(identity);

        return CheckRequest.newBuilder()
            .setResource(
                ObjectReference.newBuilder()
                    .setType(ObjectType.newBuilder().setName(resourceType.getKesselName()).build())
                    .setId(resourceId)
                    .build()
            )
            .setRelation(permission.getKesselPermissionName())
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setName(type).build())
                            .setId(identity.getName())
                            .build()
                    ).build()
            ).build();
    }

    /**
     * Build a lookup request for integrations.
     * @param identity the subject's identity.
     * @param kesselPermission the permission we want to check against the
     *                         subject's integrations.
     * @return a built lookup request that aims at finding integrations for the
     * given subject.
     */
    protected LookupResourcesRequest buildLookupResourcesRequest(final RhIdentity identity, final KesselPermission kesselPermission) {
        // Extract the subject's type from the subject's identity.
        final String type = this.extractSubjectTypeFromRhIdentity(identity);

        return LookupResourcesRequest.newBuilder()
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setName(type).build())
                            .setId(identity.getName())
                    ).build()
            ).setRelation(kesselPermission.getKesselPermissionName())
            .setResourceType(ObjectType.newBuilder().setName(ResourceType.INTEGRATION.getKesselName()))
            .build();
    }

    /**
     * Extracts the subject's type from the given identity.
     * @param rhIdentity the identity to extract the subject's type from.
     * @return service account or user.
     */
    protected String extractSubjectTypeFromRhIdentity(final RhIdentity rhIdentity) {
        if (rhIdentity instanceof RhServiceAccountIdentity) {
            return KESSEL_IDENTITY_SUBJECT_SERVICE_ACCOUNT;
        } else {
            return KESSEL_IDENTITY_SUBJECT_USER;
        }
    }
}
