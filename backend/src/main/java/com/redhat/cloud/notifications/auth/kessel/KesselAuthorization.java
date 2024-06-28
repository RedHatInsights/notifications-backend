package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.rhid.RhServiceAccountIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import org.project_kessel.api.relations.v0.CheckRequest;
import org.project_kessel.api.relations.v0.CheckResponse;
import org.project_kessel.api.relations.v0.ObjectReference;
import org.project_kessel.api.relations.v0.ObjectType;
import org.project_kessel.api.relations.v0.SubjectReference;
import org.project_kessel.relations.client.CheckClient;

import java.security.Principal;

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
     * Represents the key for the "resource_type" tag used in the timer.
     */
    private static final String KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY = "resource_type";
    /**
     * Represents the timer's name to measure the time spent sending requests
     * and receiving responses from Kessel.
     */
    private static final String KESSEL_METRICS_TIMER_NAME = "notifications.kessel.requests";

    @Inject
    BackendConfig backendConfig;

    @Inject
    CheckClient checkClient;

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
        final RhIdentity identity = this.extractRhIdentity(securityContext);

        // Build the request for Kessel.
        final CheckRequest permissionCheckRequest = this.buildCheckRequest(identity, permission, resourceType, resourceId);

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Payload for the permission check: %s", identity, permission, resourceType, resourceId, permissionCheckRequest);

        // Measure the time it takes to perform the operation with Kessel.
        final Timer.Sample timer = Timer.start(this.meterRegistry);

        // Call Kessel.
        final CheckResponse response;
        try {
            response = this.checkClient.check(permissionCheckRequest);
        } catch (final StatusRuntimeException e) {
            Log.errorf(
                e,
                "[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Runtime error when querying Kessel for a permission check",
                identity, permission, resourceType, resourceId
            );

            throw e;
        }

        // Stop the timer.
        timer.stop(this.meterRegistry.timer(KESSEL_METRICS_TIMER_NAME, Tags.of(KESSEL_METRICS_TAG_PERMISSION_KEY, permission.getKesselPermissionName(), KESSEL_METRICS_TAG_RESOURCE_TYPE_KEY, resourceType.getKesselName())));

        Log.tracef("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Received payload for the permission check: %s", identity, permission, resourceType, resourceId, response);

        // Verify whether the subject has permission on the resource or not.
        if (CheckResponse.Allowed.ALLOWED_TRUE != response.getAllowed()) {
            Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission denied", identity, permission, resourceType, resourceId);

            throw new ForbiddenException();
        }

        Log.debugf("[identity: %s][permission: %s][resource_type: %s][resource_id: %s] Permission granted", identity, resourceType, permission, resourceId);
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
        //
        final String type;
        if (identity instanceof RhServiceAccountIdentity) {
            type = KESSEL_IDENTITY_SUBJECT_SERVICE_ACCOUNT;
        } else {
            type = KESSEL_IDENTITY_SUBJECT_USER;
        }

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
     * Extracts the {@link RhIdentity} object from the security context.
     * @param securityContext the security context to extract the object from.
     * @return the extracted {@link RhIdentity} object.
     */
    protected RhIdentity extractRhIdentity(final SecurityContext securityContext) {
        final Principal genericPrincipal = securityContext.getUserPrincipal();
        if (!(genericPrincipal instanceof ConsolePrincipal<?> principal)) {
            throw new IllegalStateException(String.format("unable to extract RH Identity object from principal. Expected \"Console Principal\" object type, got \"%s\"", genericPrincipal.getClass().getName()));
        }

        final ConsoleIdentity genericIdentity = principal.getIdentity();
        if (!(genericIdentity instanceof RhIdentity identity)) {
            throw new IllegalStateException(String.format("unable to extract RH Identity object from principal. Expected \"RhIdentity\" object type, got \"%s\"", genericIdentity.getClass().getName()));
        }

        return identity;
    }
}
