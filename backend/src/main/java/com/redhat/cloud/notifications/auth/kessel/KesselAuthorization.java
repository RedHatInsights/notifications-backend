package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
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

import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;

@ApplicationScoped
public class KesselAuthorization {
    /**
     * Represents the namespace to be used for the requests that must specify
     * a subject.
     */
    public static final String KESSEL_RBAC_NAMESPACE = "rbac";
    /**
     * Represents the subject's type in Kessel. As told by them, there is only
     * going to exist the "user" type in the schema.
     */
    public static final String KESSEL_IDENTITY_SUBJECT_TYPE = "principal";
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
    CheckClient checkClient;

    @Inject
    EndpointRepository endpointRepository;

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
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Runtime error when querying Kessel for a permission check with request payload: %s",
                identity, permission, resourceType, resourceId, permissionCheckRequest
            );

            throw e;
        }

        // Stop the timer.
        permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.name())));

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
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s] Runtime error when querying Kessel for integration resources with request payload: %s",
                identity, integrationPermission, ResourceType.INTEGRATION, request
            );

            throw e;
        }

        // Stop the timer.
        lookupTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_LOOKUP_RESOURCES_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, integrationPermission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.name())));

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
     * <p>Checks whether the principal is authorized to perform the given
     * action with the given integration, and returns a proper response
     * depending on the principal's authorization level.</p>
     *
     * <p>When an integration does not exist in Kessel, the Relations API
     * simply returns an "unauthorized" response, which of course does not tell
     * us whether it's a real "unauthorized" response or rather a masked "not
     * found" one. Therefore, in this case, and depending on the principal's
     * authorization, we need to determine whether we should return a
     * {@link ForbiddenException} or a {@link NotFoundException}. The decission
     * is made as follows:</p>
     *
     * <ul>
     *     <li>
     *         We first check with Kessel if the principal is authorized to
     *         perform the given action on the given integration. If Kessel
     *         returns an "authorized" response, then we know that both the
     *         integration exists and that the principal is authorized to
     *         perform the action, so we simply return and let the caller's
     *         flow continue.
     *     </li>
     *     <li>
     *         When the principal is "unauthorized", we need to verify that the
     *         integration exists in our database. When it does, we know that
     *         the principal is simply not authorized to perform the action,
     *         so it is safe to throw a {@link ForbiddenException}.
     *     </li>
     *     <li>
     *         If the integration does not exist, we cannot simply throw a
     *         {@link NotFoundException}, because we do not really know if the
     *         principal is authorized to know that in the given workspace they
     *         might be trying to perform the action. Therefore, we check for
     *         the {@link WorkspacePermission#INTEGRATIONS_VIEW} permission at
     *         the workspace level. When the principal is "unauthorized", the
     *         underlying {@link KesselAuthorization#hasPermissionOnResource(SecurityContext, KesselPermission, ResourceType, String)}
     *         method will already throw a {@link ForbiddenException}. On the
     *         other hand, if the principal is authorized to view integrations
     *         in the workspace, then they are authorized to know that the
     *         requested integration does not simply exist.
     *     </li>
     * </ul>
     * @param securityContext the security context to pull the principal from.
     * @param permission the permission we need to check for the principal.
     * @param integrationId the integration's identifier.
     */
    public void isPrincipalAuthorizedAndDoesIntegrationExist(final SecurityContext securityContext, final IntegrationPermission permission, final UUID integrationId) {
        // First perform a normal check on the integration. If the principal is
        // authorized for that permission it means that both the integration
        // exists and that the principal has permission to perform that action.
        // Therefore, there is nothing else we need to do.
        try {
            this.hasPermissionOnResource(securityContext, permission, ResourceType.INTEGRATION, integrationId.toString());
            return;
        } catch (final ForbiddenException ignored) { }

        // If the principal did not have permission to perform the action but
        // the integration does exist, that means that we can be sure that the
        // principal is simply unauthorized to perform that action.
        if (this.endpointRepository.existsByUuidAndOrgId(integrationId, SecurityContextUtil.getOrgId(securityContext))) {
            throw new ForbiddenException();
        }

        // When the integration does not exist in our database, we need to
        // determine if the principal is supposed to know that the integration
        // does not exist. For that, we need to make sure that they can view
        // integrations in the given workspace.
        this.hasPermissionOnResource(securityContext, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        // Finally, if the principal is allowed to "view" integrations in the
        // workspace, then they are authorized to know that the integration
        // they specified simply does not exist on our end.
        final JsonObject responseBody = new JsonObject();
        responseBody.put("error", "Integration not found");

        throw new NotFoundException(responseBody.encode());
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
        return CheckRequest.newBuilder()
            .setResource(
                ObjectReference.newBuilder()
                    .setType(resourceType.getKesselObjectType())
                    .setId(resourceId)
                    .build()
            )
            .setRelation(permission.getKesselPermissionName())
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
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
        return LookupResourcesRequest.newBuilder()
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
                            .setId(identity.getName())
                    ).build()
            ).setRelation(kesselPermission.getKesselPermissionName())
            .setResourceType(ResourceType.INTEGRATION.getKesselObjectType())
            .build();
    }
}
