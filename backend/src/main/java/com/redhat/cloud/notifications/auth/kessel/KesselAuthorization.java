package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Endpoint;
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
import org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta1.authz.CheckRequest;
import org.project_kessel.api.inventory.v1beta1.authz.CheckResponse;
import org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta1.authz.ObjectReference;
import org.project_kessel.api.inventory.v1beta1.authz.ObjectType;
import org.project_kessel.api.inventory.v1beta1.authz.SubjectReference;
import org.project_kessel.api.relations.v1beta1.LookupResourcesRequest;
import org.project_kessel.api.relations.v1beta1.LookupResourcesResponse;
import org.project_kessel.api.relations.v1beta1.RequestPagination;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    /**
     * Represents the counter name to count permission check requests.
     */
    public static final String KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME = "notifications.kessel.relationships.permission.check.count";
    /**
     * Represents the counter name to count lookup resources requests.
     */
    public static final String KESSEL_METRICS_LOOKUP_RESOURCES_COUNTER_NAME = "notifications.kessel.relationships.lookup.check.count";

    protected static final String COUNTER_TAG_FAILURES = "failures";
    protected static final String COUNTER_TAG_REQUEST_RESULT = "result";
    protected static final String COUNTER_TAG_SUCCESSES = "successes";

    @Inject
    KesselCheckClient checkClient;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    BackendConfig backendConfig;

    /**
     * Should only be used for migration methods for now, and removed completely later.
     */
    @Deprecated
    @Inject
    LookupClient lookupClient;

    public void hasViewPermissionOnResource(final SecurityContext securityContext, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
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
            response = checkClient.Check(permissionCheckRequest);
        } catch (final Exception e) {
            Log.errorf(
                    e,
                    "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Unable to query Kessel for a permission on a resource",
                    identity, permission, resourceType, resourceId
            );
            meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();
            throw e;
        } finally {
            // Stop the timer.
            permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.name())));
        }

        meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES)).increment();

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (CheckResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            throw new ForbiddenException();
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
    }

    public void hasCreatePermissionOnResource(final SecurityContext securityContext, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        // Build the request for Kessel.
        final CheckForUpdateRequest permissionCheckRequest = this.buildCheckForUpdateRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckForUpdateResponse response;
        try {
            response = checkClient.CheckForUpdate(permissionCheckRequest);
        } catch (final Exception e) {
            Log.errorf(
                    e,
                    "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Unable to query Kessel for a permission on a resource",
                    identity, permission, resourceType, resourceId
            );
            meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();
            throw e;
        } finally {
            // Stop the timer.
            permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.name())));
        }

        meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES)).increment();

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (CheckForUpdateResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            throw new ForbiddenException();
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
    }

    public void hasUpdatePermissionOnResource(final SecurityContext securityContext, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        // Build the request for Kessel.
        final org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateRequest permissionCheckRequest = this.buildCheckForUpdateRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse response;
        try {
            response = checkClient.CheckForUpdate(permissionCheckRequest);
        } catch (final Exception e) {
            Log.errorf(
                    e,
                    "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Unable to query Kessel for a permission on a resource",
                    identity, permission, resourceType, resourceId
            );
            meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();
            throw e;
        } finally {
            // Stop the timer.
            permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.name())));
        }

        meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES)).increment();

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            throw new ForbiddenException();
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
    }

    /**
     * Inventory access checks perform important side effects, so the correct one should be chosen
     * Checks prior to creates/updates/writes/etc of authorized content should use "update". Otherwise, "check" is fine
     * for view/read and other non-modifying permission checks.
     */
    public enum CheckOperation {
        CHECK, // normal check: for operations where no modification of the authorized content is intended
        UPDATE // updating check: check for operations that will modify authorized content in the same transaction
    }

    CheckOperation getCheckOperation(ResourceType resourceType, KesselPermission permission) {
        if((resourceType == ResourceType.WORKSPACE) && (permission instanceof WorkspacePermission workspacePermission)) {
            return switch (workspacePermission) {
                case APPLICATIONS_VIEW,
                     BUNDLES_VIEW,
                     BEHAVIOR_GROUPS_VIEW,
                     EVENT_LOG_VIEW,
                     EVENT_TYPES_VIEW,
                     INTEGRATIONS_VIEW,
                     DAILY_DIGEST_PREFERENCE_VIEW
                        -> CheckOperation.CHECK;
                case BEHAVIOR_GROUPS_EDIT,
                     CREATE_DRAWER_INTEGRATION,
                     CREATE_EMAIL_SUBSCRIPTION_INTEGRATION,
                     INTEGRATIONS_CREATE,
                     DAILY_DIGEST_PREFERENCE_EDIT
                        -> CheckOperation.UPDATE;
            };
        } else if((resourceType == ResourceType.INTEGRATION) && (permission instanceof IntegrationPermission integrationPermission)) {
            return switch (integrationPermission) {
                case VIEW, VIEW_HISTORY -> CheckOperation.CHECK;
                case DELETE, DISABLE, EDIT, ENABLE, TEST -> CheckOperation.UPDATE;
            };
        } else {
            throw new RuntimeException("Resource/permission pair unsupported for Kessel check: "
                    + resourceType + "/" + permission);
        }
    }

    /**
     * Checks if the subject on the security context has permission on the
     * given resource. Throws
     * @param securityContext the security context to extract the subject from.
     *
     * @return true or false regarding if the user have access to requested resource.
     */
    public boolean hasPermissionOnResource(final SecurityContext securityContext, final String resourceId, final ResourceType resourceType, final KesselPermission kesselPermission) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        // Build the request for Kessel.
        CheckOperation checkOperation = getCheckOperation(resourceType, kesselPermission);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final String checkResponseAllowedString;
        try {
            if(CheckOperation.CHECK.equals(checkOperation)) {
                final CheckRequest permissionCheckRequest = this.buildCheckRequest(identity, kesselPermission, resourceType, resourceId);
                Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, kesselPermission.getKesselPermissionName(), resourceType, resourceId, permissionCheckRequest);

                CheckResponse response = this.checkClient.Check(permissionCheckRequest);
                if(response == null) {
                    checkResponseAllowedString = null;
                } else {
                    checkResponseAllowedString = response.getAllowed().name();
                }
            } else if(CheckOperation.UPDATE.equals(checkOperation)) {
                final CheckForUpdateRequest permissionCheckRequest = this.buildCheckForUpdateRequest(identity, kesselPermission, resourceType, resourceId);
                Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the update permission check: %s", identity, kesselPermission.getKesselPermissionName(), resourceType, resourceId, permissionCheckRequest);

                CheckForUpdateResponse response = this.checkClient.CheckForUpdate(permissionCheckRequest);
                if(response == null) {
                    checkResponseAllowedString = null;
                } else {
                    checkResponseAllowedString = response.getAllowed().name();
                }
            } else {
                throw new RuntimeException("Unsupported check operation: " + checkOperation);
            }
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Unable to query Kessel for a permission on a resource",
                identity, kesselPermission.getKesselPermissionName(), resourceType, resourceId
            );
            meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();
            return false;
        } finally {
            // Stop the timer.
            permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, kesselPermission.getKesselPermissionName(), Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY)));
        }

        meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES)).increment();

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, kesselPermission.getKesselPermissionName(), resourceType, resourceId, checkResponseAllowedString);

        // Verify whether the subject has permission on the resource or not.
        if (!CheckResponse.Allowed.ALLOWED_TRUE.name().equals(checkResponseAllowedString)) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, kesselPermission.getKesselPermissionName(), resourceType, resourceId);

            return false;
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, kesselPermission.getKesselPermissionName(), resourceId);
        return true;
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
        return buildCheckRequest(
                getUserId(identity),
                permission.getKesselPermissionName(),
                resourceType.getKesselObjectType().getName(),
                resourceType.getKesselObjectType().getNamespace(),
                resourceId);
    }

    protected CheckRequest buildCheckRequest(final RhIdentity identity, final RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        return buildCheckRequest(
                getUserId(identity),
                recipientsAuthorizationCriterion.getRelation(),
                recipientsAuthorizationCriterion.getType().getName(),
                recipientsAuthorizationCriterion.getType().getNamespace(),
                recipientsAuthorizationCriterion.getId());
    }

    protected CheckRequest buildCheckRequest(final String userId, final String permissionName, final String resourceTypeName, final String resourceTypeNamespace, final String resourceId) {
        return CheckRequest.newBuilder()
                .setParent(
                        org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                                .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder()
                                        .setName(resourceTypeName)
                                        .setNamespace(resourceTypeNamespace).build())
                                .setId(resourceId)
                                .build()
                )
                .setRelation(permissionName)
                .setSubject(
                        org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                                .setSubject(
                                        org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                                                .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
                                                .setId(userId)
                                                .build()
                                ).build()
                ).build();
    }

    protected CheckForUpdateRequest buildCheckForUpdateRequest(final RhIdentity identity, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        return CheckForUpdateRequest.newBuilder()
                .setParent(
            org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder()
                        .setName(resourceType.getKesselObjectType().getName())
                        .setNamespace(resourceType.getKesselObjectType().getNamespace()).build())
                .setId(resourceId)
                .build()
        )
        .setRelation(permission.getKesselPermissionName())
        .setSubject(
            org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                .setSubject(
                    org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                        .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
                        .setId(getUserId(identity))
                        .build()
                ).build()
        ).build();
    }

    /**
     * Gets the user identifier from the {@link RhIdentity} object.
     * @param identity the object to extract the identifier from.
     * @return the user ID in the format that Kessel expects.
     */
    private String getUserId(RhIdentity identity) {
        return backendConfig.getKesselDomain() + "/" + identity.getUserId();
    }

    /**
     * List integrations of a specific workspace
     * Relations-api is deprecated. Could maybe move to KesselAssets.listIntegrations() with some changes.
     * (The problem is that this method requires an identity as opposed to using the workspace as the subject.)
     * @param workspaceId specific workspace ID
     * @return endpoints uuid list
     */
    @Deprecated
    public Set<UUID> listWorkspaceIntegrations(final UUID workspaceId) {

        // Prepare the set of UUIDs we are going to receive from Kessel.
        final Set<UUID> uuids = new HashSet<>();

        // Every response coming from Kessel has a continuation token. In order
        // to know when to stop querying for resources, we need to keep track
        // of the old continuation token and the one from the latest received
        // element. Once the old one and the new one are the same, we don't
        // have to keep querying for more resources.
        String continuationToken;
        String newContinuationToken = "";
        do {
            // Replace the old continuation token with the new one. This way
            // the token gets used
            continuationToken = newContinuationToken;

            // Build the lookup request for Kessel.
            final LookupResourcesRequest request = this.buildWorkspaceIntegrationsLookupResourcesRequest(workspaceId, continuationToken);

            Log.tracef("Payload for the resource lookup: %s", request);

            // Make the request to Kessel.
            final Iterator<LookupResourcesResponse> responses;
            try {
                responses = lookupClient.lookupResources(request);
            } catch (final Exception e) {
                Log.errorf(
                    e,
                    "Runtime error when querying Kessel for integration resources with request payload: %s", request
                );

                throw e;
            }

            // Iterate over the incoming results.
            while (responses.hasNext()) {
                final LookupResourcesResponse response = responses.next();

                Log.tracef("Received payload for the resource lookup: %s", response);

                uuids.add(UUID.fromString(response.getResource().getId()));

                // Update the continuation token every time, to make sure we
                // grab the last streamed element's continuation token.
                newContinuationToken = response.getPagination().getContinuationToken();
            }
        } while (!continuationToken.equals(newContinuationToken));

        return uuids;
    }

    /**
     * Relations-api is deprecated.
     * @param workspaceId
     * @param continuationToken
     * @return
     */
    @Deprecated
    protected LookupResourcesRequest buildWorkspaceIntegrationsLookupResourcesRequest(final UUID workspaceId, final String continuationToken) {
        final LookupResourcesRequest.Builder requestBuilder = LookupResourcesRequest.newBuilder()
            .setSubject(
                    org.project_kessel.api.relations.v1beta1.SubjectReference.newBuilder()
                    .setSubject(
                            org.project_kessel.api.relations.v1beta1.ObjectReference.newBuilder()
                            .setType(inventoryObjectTypeToRelations(ResourceType.WORKSPACE.getKesselObjectType()))
                            .setId(workspaceId.toString())
                    ).build()
            ).setRelation("workspace")
            .setResourceType(inventoryObjectTypeToRelations(ResourceType.INTEGRATION.getKesselObjectType()));

        // Include the continuation token in the request to resume fetching
        // resources right where we last left off.
        if (continuationToken != null && !continuationToken.isBlank()) {
            requestBuilder.setPagination(
                RequestPagination.newBuilder()
                    .setContinuationToken(continuationToken)
                    .setLimit(this.backendConfig.getKesselRelationsLookupResourceLimit())
                    .build()
            );
        }

        return requestBuilder.build();
    }

    /**
     * Relations-api is deprecated
     * @param objectType
     * @return
     */
    @Deprecated
    protected org.project_kessel.api.relations.v1beta1.ObjectType inventoryObjectTypeToRelations(ObjectType objectType) {
        return org.project_kessel.api.relations.v1beta1.ObjectType.newBuilder()
                .setName(objectType.getName())
                .setNamespace(objectType.getNamespace())
                .build();
    }
}
