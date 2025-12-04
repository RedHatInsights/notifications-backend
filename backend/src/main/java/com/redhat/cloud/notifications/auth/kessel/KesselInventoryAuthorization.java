package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ReporterReference;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.SubjectReference;

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
            response = this.checkClient.check(permissionCheckRequest);
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
        if (Allowed.ALLOWED_TRUE != response.getAllowed()) {
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
            response = this.checkClient.checkForUpdate(permissionCheckRequest);
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
        if (Allowed.ALLOWED_TRUE != response.getAllowed()) {
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
            response = this.checkClient.check(permissionCheckRequest);
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
        if (response == null || Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            return false;
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
        return true;
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

    private ResourceReference buildObjectReference(KesselInventoryResourceType resourceType, String resourceId) {
        return ResourceReference.newBuilder()
            .setReporter(resourceType.getReporter())
            .setResourceType(resourceType.getResourceType())
            .setResourceId(resourceId)
            .build();
    }

    private SubjectReference buildSubjectReference(RhIdentity identity) {
        return SubjectReference.newBuilder()
            .setResource(ResourceReference.newBuilder()
                .setReporter(ReporterReference.newBuilder()
                    .setType(KESSEL_RBAC_NAMESPACE)
                    .build())
                .setResourceType(KESSEL_IDENTITY_SUBJECT_TYPE)
                .setResourceId(backendConfig.getKesselDomain() + "/" + identity.getUserId())
                .build())
            .build();
    }

    private CheckRequest buildCheckRequest(RhIdentity identity, KesselPermission permission, KesselInventoryResourceType resourceType, String resourceId) {
        return CheckRequest.newBuilder()
            .setObject(buildObjectReference(resourceType, resourceId))
            .setRelation(permission.getKesselPermissionName())
            .setSubject(buildSubjectReference(identity))
            .build();
    }

    private CheckForUpdateRequest buildCheckForUpdateRequest(RhIdentity identity, KesselPermission permission, KesselInventoryResourceType resourceType, String resourceId) {
        return CheckForUpdateRequest.newBuilder()
            .setObject(buildObjectReference(resourceType, resourceId))
            .setRelation(permission.getKesselPermissionName())
            .setSubject(buildSubjectReference(identity))
            .build();
    }

    private CheckRequest buildCheckRequest(RhIdentity identity, RecipientsAuthorizationCriterion criterion) {
        return CheckRequest.newBuilder()
            .setObject(ResourceReference.newBuilder()
                .setReporter(ReporterReference.newBuilder()
                    .setType(criterion.getType().getNamespace()).build())
                .setResourceType(criterion.getType().getName())
                .setResourceId(criterion.getId())
                .build())
            .setRelation(criterion.getRelation())
            .setSubject(buildSubjectReference(identity))
            .build();
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
                case EVENTS_VIEW,
                     INTEGRATIONS_VIEW,
                     NOTIFICATIONS_VIEW
                    -> CheckOperation.CHECK;
                case INTEGRATIONS_EDIT,
                     NOTIFICATIONS_EDIT
                    -> CheckOperation.UPDATE;
            };
        } else {
            throw new IllegalArgumentException(String.format("Resource/permission pair unsupported for Kessel check: %s/%s", resourceType, permission));
        }
    }
}
