package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ReporterReference;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.SubjectReference;

import java.util.UUID;

import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE;
import static com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization.KESSEL_RBAC_NAMESPACE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Helps mocking Kessel's responses for testing purposes. It requires the
 * Kessel clients to be mocked by the test class for these to work.
 */
@ApplicationScoped
public class KesselTestHelper {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselCheckClient kesselCheckClient;

    @Inject
    WorkspaceUtils workspaceUtils;

    /**
     * The {@link UUID} that the {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * method will return no matter what workspace is passed to it. The re
     */
    public static final UUID RBAC_DEFAULT_WORKSPACE_ID = UUID.randomUUID();

    /**
     * Mocks the call to {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * and returns the identifier defined in {@link KesselTestHelper#RBAC_DEFAULT_WORKSPACE_ID}.
     * @param orgId the organization identifier the mocked method is expecting
     *              to be able to return the mocked value.
     */
    public void mockDefaultWorkspaceId(final String orgId) {
        when(this.workspaceUtils.getDefaultWorkspaceId(orgId)).thenReturn(RBAC_DEFAULT_WORKSPACE_ID);
    }

    /**
     * Mocks the call to {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * by returning the given ID for the given org ID.
     * @param orgId the organization identifier the mocked method is expecting
     *              to be able to return the mocked value.
     * @param returningWorkspaceId the mocked identifier we are going to
     *                             simulate that RBAC returns.
     */
    public void mockDefaultWorkspaceId(final String orgId, final UUID returningWorkspaceId) {
        when(this.workspaceUtils.getDefaultWorkspaceId(orgId)).thenReturn(returningWorkspaceId);
    }

    /**
     * Mocks the {@link KesselCheckClient} so that it returns an authorized response
     * for the given permission.
     * @param subjectUsername the subject's name as sent in the "x-rh-identity"
     *                        header.
     * @param permission the permission that will be checked in the handler.
     * @param resourceType the resource type against which the permission will
     *                     be checked.
     * @param resourceId the resource's identifier.
     */
    public void mockKesselPermission(final String subjectUsername, final KesselPermission permission, final KesselResourceType resourceType, final String resourceId) {
        this.mockKesselPermission(subjectUsername, permission, resourceType, resourceId, Allowed.ALLOWED_TRUE);
    }

    /**
     * Mocks the {@link KesselCheckClient} so that it returns an authorized response
     * for the given permission.
     * @param subjectUsername the subject's name as sent in the "x-rh-identity"
     *                        header.
     * @param permission the permission that will be checked in the handler.
     * @param resourceType the resource type against which the permission will
     *                     be checked.
     * @param resourceId the resource's identifier.
     * @param allowedResponse the returned response from Kessel.
     */
    public void mockKesselPermission(final String subjectUsername, final KesselPermission permission, final KesselResourceType resourceType, final String resourceId, final Allowed allowedResponse) {
        if (!this.backendConfig.isKesselEnabled(anyString())) {
            return;
        }

        ResourceReference object = ResourceReference.newBuilder()
            .setReporter(resourceType.getReporter())
            .setResourceType(resourceType.getResourceType())
            .setResourceId(resourceId)
            .build();

        SubjectReference subject = SubjectReference.newBuilder()
            .setResource(ResourceReference.newBuilder()
                .setReporter(ReporterReference.newBuilder()
                    .setType(KESSEL_RBAC_NAMESPACE)
                    .build())
                .setResourceType(KESSEL_IDENTITY_SUBJECT_TYPE)
                .setResourceId(backendConfig.getKesselDomain() + "/" + subjectUsername)
                .build())
            .build();

        when(
            kesselCheckClient.check(CheckRequest.newBuilder()
                .setObject(object)
                .setRelation(permission.getKesselPermissionName())
                .setSubject(subject)
                .build()
            )
        ).thenReturn(CheckResponse.newBuilder()
            .setAllowed(allowedResponse)
            .build()
        );

        when(
            kesselCheckClient.checkForUpdate(CheckForUpdateRequest.newBuilder()
                .setObject(object)
                .setRelation(permission.getKesselPermissionName())
                .setSubject(subject)
                .build()
            )
        ).thenReturn(CheckForUpdateResponse.newBuilder()
            .setAllowed(allowedResponse)
            .build()
        );
    }

    /**
     * Mocks the {@link BackendConfig#isKesselEnabled(String)} so that it
     * returns the given boolean flag when asked, and also makes the {@link KesselCheckClient}
     * return an "allowed" response when the flag is {@code true}.
     * @param isKesselEnabled is Kessel enabled for the test?
     */
    public void mockKessel(final boolean isKesselEnabled) {
        when(backendConfig.isKesselEnabled(anyString()))
            .thenReturn(isKesselEnabled);

        if (!backendConfig.isKesselEnabled(anyString())) {
            return;
        }

        // Default to an unauthorized response.
        when(kesselCheckClient.check(Mockito.any()))
            .thenReturn(CheckResponse.newBuilder()
                .setAllowed(Allowed.ALLOWED_FALSE)
                .build()
            );

        // Default to an unauthorized response.
        when(kesselCheckClient.checkForUpdate(Mockito.any()))
            .thenReturn(CheckForUpdateResponse.newBuilder()
                .setAllowed(Allowed.ALLOWED_FALSE)
                .build()
            );
    }
}
