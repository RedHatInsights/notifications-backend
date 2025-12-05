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
import io.smallrye.mutiny.Multi;
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
import org.project_kessel.api.inventory.v1beta1.authz.ObjectReference;
import org.project_kessel.api.inventory.v1beta1.authz.ObjectType;
import org.project_kessel.api.inventory.v1beta1.authz.SubjectReference;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsRequest;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsResponse;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class KesselInventoryAuthorization {
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
    private static final String KESSEL_METRICS_LIST_INTEGRATIONS_TIMER_NAME = "notifications.kessel.inventory.list.integrations.requests";
    /**
     * Represents the timer's name to measure the time spent checking for a
     * particular permission for a subject.
     */
    private static final String KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME = "notifications.kessel.inventory.permission.check.requests";
    /**
     * Represents the counter name to count permission check requests.
     */
    public static final String KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME = "notifications.kessel.inventory.permission.check.count";
    /**
     * Represents the counter name to count lookup resources requests.
     */
    public static final String KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME = "notifications.kessel.inventory.list.integrations.count";

    protected static final String COUNTER_TAG_FAILURES = "failures";
    protected static final String COUNTER_TAG_REQUEST_RESULT = "result";
    protected static final String COUNTER_TAG_SUCCESSES = "successes";

    @Inject
    KesselCheckClient checkClient;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    BackendConfig backendConfig;

    @Inject
    NotificationsIntegrationClient notificationsIntegrationClient;

    /**
     * Filters the given list of integrations and leaves only the ones for
     * which the principal has authorization to view. Useful for
     * "post-filtering" the integrations once we have fetched them from the
     * database, and we want to remove the ones that the principal does not
     * have authorization for.
     * @param securityContext the security context from which the principal is
     *                        extracted.
     * @param endpoints the list of integrations to check.
     * @return a filtered list of integrations that the principal has
     * permission to view. The original list is kept untouched to avoid any
     * issues to avoid "immutable lists cannot be modified" issues.
     */
    public List<Endpoint> filterUnauthorizedIntegrations(final SecurityContext securityContext, final List<Endpoint> endpoints) {
        final List<Endpoint> resultingList = new ArrayList<>();

        for (final Endpoint endpoint : endpoints) {
            try {
                this.hasPermissionOnResource(securityContext, IntegrationPermission.VIEW, KesselInventoryResourceType.INTEGRATION, endpoint.getId().toString());
                resultingList.add(endpoint);
            } catch (final ForbiddenException ignored) {
            }
        }

        return resultingList;
    }

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
    public void hasPermissionOnResource(final SecurityContext securityContext, final KesselPermission permission, final KesselInventoryResourceType resourceType, final String resourceId) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);

        CheckOperation checkOperation = getCheckOperation(resourceType, permission);

        if (CheckOperation.CHECK.equals(checkOperation)) {
            checkReadOnly(permission, resourceType, resourceId, identity);
        } else {
            checkForUpdate(permission, resourceType, resourceId, identity);
        }
    }

    private void checkReadOnly(KesselPermission permission, KesselInventoryResourceType resourceType, String resourceId, RhIdentity identity) {
        // Build the request for Kessel.
        final CheckRequest permissionCheckRequest = this.buildCheckRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckResponse response;
        try {
            response = this.checkClient.Check(permissionCheckRequest);
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

    private void checkForUpdate(KesselPermission permission, KesselInventoryResourceType resourceType, String resourceId, RhIdentity identity) {
        // Build the request for Kessel.
        final CheckForUpdateRequest permissionCheckRequest = this.buildCheckForUpdateRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckForUpdateResponse response;
        try {
            response = this.checkClient.CheckForUpdate(permissionCheckRequest);
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

    /**
     * Checks if the subject on the security context has permission on the
     * given resource. Throws
     * @param securityContext the security context to extract the subject from.
     * @param authorizationCriterion the authorization criterion.
     *
     * @return true or false regarding if the user have access to requested resource.
     */
    public boolean hasPermissionOnResource(final SecurityContext securityContext, final RecipientsAuthorizationCriterion authorizationCriterion) {
        // Identify the subject.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);
        final String permission = authorizationCriterion.getRelation();
        final String resourceType = authorizationCriterion.getType().toString();
        final String resourceId = authorizationCriterion.getId();

        // Build the request for Kessel.
        final CheckRequest permissionCheckRequest = this.buildCheckRequest(identity, authorizationCriterion);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample permissionCheckTimer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckResponse response;
        try {
            response = this.checkClient.Check(permissionCheckRequest);
        } catch (final Exception e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Unable to query Kessel for a permission on a resource",
                identity, permission, resourceType, resourceId
            );
            meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();
            return false;
        } finally {
            // Stop the timer.
            permissionCheckTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_PERMISSION_CHECK_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission, Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, authorizationCriterion.getType().getName())));
        }

        meterRegistry.counter(KESSEL_METRICS_PERMISSION_CHECK_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES)).increment();

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (response == null || CheckResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            return false;
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
        return true;
    }

    /**
     * Looks up the integrations the security context's subject has the given
     * permission for. Useful for when we want to "pre-filter" the integrations
     * the principal has authorization for.
     * @param securityContext the security context holding the subject's
     *                        identity.
     * @param integrationPermission the integration's permission we want to use
     *                              to filter the target integrations with.
     * @return a set of integration IDs the user has permission to access.
     */
    public Set<UUID> lookupAuthorizedIntegrations(final SecurityContext securityContext, final UUID workspaceId, final IntegrationPermission integrationPermission) {
        // Build the request for Kessel's inventory.
        final RhIdentity identity = SecurityContextUtil.extractRhIdentity(securityContext);
        final ListNotificationsIntegrationsRequest request = this.buildListIntegrationRequest(identity, workspaceId, integrationPermission);

        Log.tracef("[identity: %s][workspaceID: %s][permission: %s] Payload for the listNotificationsIntegrations check: %s", identity, workspaceId, integrationPermission, request);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample listIntegrationTimer = Timer.start(this.meterRegistry);
        LocalDateTime startTime = LocalDateTime.now();
        Set<UUID> authorizedIds;
        try {
            // Send the request to the inventory.
            final Multi<ListNotificationsIntegrationsResponse> responses = this.notificationsIntegrationClient.listNotificationsIntegrations(request);

            authorizedIds = responses.map(ListNotificationsIntegrationsResponse::getIntegrations)
                .map(i -> i.getReporterData().getLocalResourceId())
                .map(UUID::fromString)
                .collect()
                .asSet()
                .await()
                .atMost(Duration.ofSeconds(30));

            Log.debugf("[identity: %s][workspaceID: %s][permission: %s] listNotificationsIntegrations returned %d integrations", identity,  workspaceId, integrationPermission, authorizedIds.size());

        } catch (final Exception e) {
            meterRegistry.counter(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();

            throw e;
        } finally {
            // Stop the timer.
            listIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_LIST_INTEGRATIONS_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.name())));
        }

        Duration duration = Duration.between(startTime, LocalDateTime.now());
        if (Duration.ofMillis(300).compareTo(duration) < 0) {
            Log.warnf("listNotificationsIntegrations service response time was %dms for request %s and returned %d integrations", duration.toMillis(), request, authorizedIds.size());
        }
        return authorizedIds;
    }

    protected ListNotificationsIntegrationsRequest buildListIntegrationRequest(final RhIdentity identity, final UUID workspaceId, final IntegrationPermission integrationPermission) {
        return ListNotificationsIntegrationsRequest.newBuilder()
            .setResourceType(KesselInventoryResourceType.INTEGRATION.getKesselObjectType())
            .setParent(org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                .setType(KesselInventoryResourceType.WORKSPACE.getKesselObjectType())
                .setId(workspaceId.toString())
                .build())
            .setSubject(org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                .setSubject(org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                    .setId(getUserId(identity))
                    .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
                    .build())
                .build())
            .setRelation(integrationPermission.getKesselPermissionName())
            .build();
    }

    // used by migration consistency check service only
    public Set<UUID> listWorkspaceIntegrations(final UUID workspaceId) {
        // Build the request for Kessel's inventory.
        final ListNotificationsIntegrationsRequest request = this.buildListIntegrationOfWorkspaceRequest(workspaceId);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample listIntegrationTimer = Timer.start(this.meterRegistry);

        Set<UUID> authorizedIds;
        try {
            // Send the request to the inventory.
            final Multi<ListNotificationsIntegrationsResponse> responses = this.notificationsIntegrationClient.listNotificationsIntegrations(request);

            authorizedIds = responses.map(ListNotificationsIntegrationsResponse::getIntegrations)
                .map(i -> i.getReporterData().getLocalResourceId())
                .map(UUID::fromString)
                .collect()
                .asSet()
                .await()
                .atMost(Duration.ofSeconds(30));

        } catch (final Exception e) {
            meterRegistry.counter(KESSEL_METRICS_LIST_INTEGRATIONS_COUNTER_NAME, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES)).increment();

            throw e;
        } finally {
            // Stop the timer.
            listIntegrationTimer.stop(this.meterRegistry.timer(KESSEL_METRICS_LIST_INTEGRATIONS_TIMER_NAME, Tags.of(Constants.KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, ResourceType.INTEGRATION.name())));
        }

        return authorizedIds;
    }

    protected ListNotificationsIntegrationsRequest buildListIntegrationOfWorkspaceRequest(final UUID workspaceId) {
        return ListNotificationsIntegrationsRequest.newBuilder()
            .setResourceType(KesselInventoryResourceType.INTEGRATION.getKesselObjectType())
            .setParent(org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                .setType(KesselInventoryResourceType.WORKSPACE.getKesselObjectType())
                .setId(workspaceId.toString())
                .build())
            .setSubject(org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                .setSubject(org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                    .setType(KesselInventoryResourceType.WORKSPACE.getKesselObjectType())
                    .setId(workspaceId.toString())
                    .build())
                .build())
            .setRelation("workspace")
            .build();
    }

    /**
     * Checks whether the provided principal has the specified permission on
     * the given integration, and throws a {@link NotFoundException} if they
     * do not.
     * @param securityContext the security context to extract the principal
     *                        from.
     * @param integrationPermission the integration permission we want to
     *                              check.
     * @param integrationId the integration's identifier.
        */
    public void hasPermissionOnIntegration(final SecurityContext securityContext, final IntegrationPermission integrationPermission, final UUID integrationId) {
        try {
            this.hasPermissionOnResource(securityContext, integrationPermission, KesselInventoryResourceType.INTEGRATION, integrationId.toString());
        } catch (final ForbiddenException ignored) {
            final JsonObject responseBody = new JsonObject();
            responseBody.put("error", "Integration not found");

            throw new NotFoundException(responseBody.encode());
        }
    }

    /**
     * Checks whether the provided principal has the specified permission on
     * the given workspace.
     * @param securityContext the security context to extract the principal
     *                        from.
     * @param workspacePermission the workspace permission we want to
     *                            check.
     * @param workspaceId the workspace's identifier.
     */
    public void hasPermissionOnWorkspace(final SecurityContext securityContext, final WorkspacePermission workspacePermission, final UUID workspaceId) {
        this.hasPermissionOnResource(securityContext, workspacePermission, KesselInventoryResourceType.WORKSPACE, workspaceId.toString());
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
    protected CheckRequest buildCheckRequest(final RhIdentity identity, final KesselPermission permission, final KesselInventoryResourceType resourceType, final String resourceId) {
        return CheckRequest.newBuilder()
            .setParent(
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
                            .setId(getUserId(identity))
                            .build()
                    ).build()
            ).build();
    }

    protected CheckForUpdateRequest buildCheckForUpdateRequest(final RhIdentity identity, final KesselPermission permission, final KesselInventoryResourceType resourceType, final String resourceId) {
        return CheckForUpdateRequest.newBuilder()
            .setParent(
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
                            .setId(getUserId(identity))
                            .build()
                    ).build()
            ).build();
    }

    protected CheckRequest buildCheckRequest(final RhIdentity identity, final RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        return CheckRequest.newBuilder()
            .setParent(
                ObjectReference.newBuilder()
                    .setType(ObjectType.newBuilder()
                        .setNamespace(recipientsAuthorizationCriterion.getType().getNamespace())
                        .setName(recipientsAuthorizationCriterion.getType().getName()).build())
                    .setId(recipientsAuthorizationCriterion.getId())
                    .build()
            )
            .setRelation(recipientsAuthorizationCriterion.getRelation())
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setNamespace(KESSEL_RBAC_NAMESPACE).setName(KESSEL_IDENTITY_SUBJECT_TYPE).build())
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
     * Inventory access checks perform important side effects, so the correct one should be chosen
     * Checks prior to creates/updates/writes/etc of authorized content should use "update". Otherwise, "check" is fine
     * for view/read and other non-modifying permission checks.
     */
    public enum CheckOperation {
        CHECK, // normal check: for operations where no modification of the authorized content is intended
        UPDATE // updating check: check for operations that will modify authorized content in the same transaction
    }

    CheckOperation getCheckOperation(KesselInventoryResourceType resourceType, KesselPermission permission) {
        if ((resourceType == KesselInventoryResourceType.WORKSPACE) && (permission instanceof WorkspacePermission workspacePermission)) {
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
        } else if ((resourceType == KesselInventoryResourceType.INTEGRATION) && (permission instanceof IntegrationPermission integrationPermission)) {
            return switch (integrationPermission) {
                case VIEW, VIEW_HISTORY -> CheckOperation.CHECK;
                case DELETE, DISABLE, EDIT, ENABLE, TEST -> CheckOperation.UPDATE;
            };
        } else {
            throw new IllegalArgumentException(String.format("Resource/permission pair unsupported for Kessel check: %s/%s", resourceType, permission));
        }
    }
}
