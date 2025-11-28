package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.inventory.v1beta2.Allowed;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ReporterReference;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.SubjectReference;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

/**
 * Helps mocking Kessel's responses for testing purposes. It requires the
 * Kessel clients to be mocked by the test class for these to work.
 */
@ApplicationScoped
public class KesselTestHelper {

    @Inject
    BackendConfig backendConfig;

    @Inject
    WorkspaceUtils workspaceUtils;

    /**
     * The {@link UUID} that the {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * method will return no matter what workspace is passed to it. The re
     */
    public static final UUID RBAC_DEFAULT_WORKSPACE_ID = UUID.randomUUID();

    public CheckRequest buildCheckRequest(String subjectUsername, KesselPermission permission) {
        return buildCheckRequest(DEFAULT_ORG_ID, subjectUsername, permission);
    }

    public CheckRequest buildCheckRequest(String orgId, String subjectUsername, KesselPermission permission) {
        return CheckRequest.newBuilder()
            .setObject(buildResourceReference(orgId))
            .setRelation(permission.getKesselPermissionName())
            .setSubject(buildSubjectReference(subjectUsername))
            .build();
    }

    public CheckResponse buildCheckResponse(Allowed allowedResponse) {
        return CheckResponse.newBuilder()
            .setAllowed(allowedResponse)
            .build();
    }

    public CheckForUpdateRequest buildCheckForUpdateRequest(String subjectUsername, KesselPermission permission) {
        return buildCheckForUpdateRequest(DEFAULT_ORG_ID, subjectUsername, permission);
    }

    public CheckForUpdateRequest buildCheckForUpdateRequest(String orgId, String subjectUsername, KesselPermission permission) {
        return CheckForUpdateRequest.newBuilder()
            .setObject(buildResourceReference(orgId))
            .setRelation(permission.getKesselPermissionName())
            .setSubject(buildSubjectReference(subjectUsername))
            .build();
    }

    public CheckForUpdateResponse buildCheckForUpdateResponse(Allowed allowedResponse) {
        return CheckForUpdateResponse.newBuilder()
            .setAllowed(allowedResponse)
            .build();
    }

    private ResourceReference buildResourceReference(String orgId) {
        return ResourceReference.newBuilder()
            .setReporter(KesselInventoryResourceType.WORKSPACE.getReporter())
            .setResourceType(KesselInventoryResourceType.WORKSPACE.getResourceType())
            .setResourceId(workspaceUtils.getDefaultWorkspaceId(orgId).toString())
            .build();
    }

    private SubjectReference buildSubjectReference(String subjectUsername) {
        return SubjectReference.newBuilder()
            .setResource(ResourceReference.newBuilder()
                .setReporter(ReporterReference.newBuilder()
                    .setType(KesselInventoryAuthorization.KESSEL_RBAC_NAMESPACE)
                    .build())
                .setResourceType(KesselInventoryAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE)
                .setResourceId(backendConfig.getKesselDomain() + "/" + subjectUsername)
                .build())
            .build();
    }
}
