package com.redhat.cloud.notifications.auth.kessel;

import com.redhat.cloud.notifications.auth.kessel.permission.KesselPermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsResponse;
import org.project_kessel.api.inventory.v1beta1.resources.NotificationsIntegration;
import org.project_kessel.api.inventory.v1beta1.resources.ReporterData;
import org.project_kessel.api.relations.v1beta1.CheckRequest;
import org.project_kessel.api.relations.v1beta1.CheckResponse;
import org.project_kessel.api.relations.v1beta1.LookupResourcesResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.api.relations.v1beta1.SubjectReference;
import org.project_kessel.inventory.client.KesselCheckClient;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * Helps mocking Kessel's responses for testing purposes. It requires the
 * Kessel clients to be mocked by the test class for these to work.
 */
@ApplicationScoped
public class KesselTestHelper {
    @Inject
    BackendConfig backendConfig;

    @Inject
    CheckClient checkClient;

    @Inject
    KesselCheckClient kesselCheckClient;

    @Inject
    LookupClient lookupClient;

    @Inject
    NotificationsIntegrationClient notificationsIntegrationClient;

    @Inject
    WorkspaceUtils workspaceUtils;

    /**
     * The {@link UUID} that the {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * method will return no matter what workspace is passed to it. The re
     */
    public static final UUID RBAC_DEFAULT_WORKSPACE_ID = UUID.randomUUID();

    /**
     * Defines a default Kessel domain that will get returned every time
     * that the {@link BackendConfig#getKesselDomain()} method is called.
     */
    public static final String DEFAULT_KESSEL_DOMAIN = "redhat";

    /**
     * Mocks the {@link LookupClient} so that it simulates that no authorized
     * integrations were fetched from Kessel.
     * @deprecated In favor of "post-filtering". Looking up integrations makes
     * Kessel have to send the entire set of identifiers beforehand, and since
     * they also have a limit on the number of elements they return, this
     * caused issues when requesting a specific integration in a big
     * collection. More information on <a href="https://issues.redhat.com/browse/RHCLOUD-37430">
     * RHCLOUD-37430</a>.
     */
    public void mockAuthorizedIntegrationsLookup() {
        this.mockAuthorizedIntegrationsLookup(Collections.emptySet());
    }

    /**
     * Mocks the {@link LookupClient} so that it simulates an incoming response
     * from Kessel which contains the specified integrations.
     * @param authorizedIntegrationsIds the set of IDs that the lookup client
     *                                  will return in an iterator.
     * @deprecated In favor of "post-filtering". Looking up integrations makes
     * Kessel have to send the entire set of identifiers beforehand, and since
     * they also have a limit on the number of elements they return, this
     * caused issues when requesting a specific integration in a big
     * collection. More information on <a href="https://issues.redhat.com/browse/RHCLOUD-37430">
     * RHCLOUD-37430</a>.
     */
    public void mockAuthorizedIntegrationsLookup(final Set<UUID> authorizedIntegrationsIds) {
        // It does not make sense to mock anything if we are not testing with
        // Kessel.
        if (!this.backendConfig.isKesselRelationsEnabled(anyString())) {
            return;
        }

        final Set<LookupResourcesResponse> lookupResourcesResponses = new HashSet<>();
        for (final UUID id : authorizedIntegrationsIds) {
            final LookupResourcesResponse mockedResponse = LookupResourcesResponse
                .newBuilder()
                .setResource(
                    ObjectReference
                        .newBuilder()
                        .setId(id.toString())
                ).build();

            lookupResourcesResponses.add(mockedResponse);
        }

        Mockito.when(
            this.lookupClient.lookupResources(Mockito.any())
        ).thenReturn(
            lookupResourcesResponses.iterator()
        );

        Set<ListNotificationsIntegrationsResponse> setList = new HashSet<>();
        for (UUID uuid : authorizedIntegrationsIds) {
            setList.add(
                ListNotificationsIntegrationsResponse.newBuilder().setIntegrations(
                    NotificationsIntegration.newBuilder().setReporterData(
                        ReporterData.newBuilder().setLocalResourceId(uuid.toString()).build()
                    ).build()
                ).build()
            );
        }

        Mockito.when(
            this.notificationsIntegrationClient.listNotificationsIntegrations(Mockito.any())
        ).thenReturn(
            Multi.createFrom().items(setList.stream())
        );
    }

    /**
     * Mocks the call to {@link WorkspaceUtils#getDefaultWorkspaceId(String)}
     * and returns the identifier defined in {@link KesselTestHelper#RBAC_DEFAULT_WORKSPACE_ID}.
     * @param orgId the organization identifier the mocked method is expecting
     *              to be able to return the mocked value.
     */
    public void mockDefaultWorkspaceId(final String orgId) {
        Mockito.when(this.workspaceUtils.getDefaultWorkspaceId(orgId)).thenReturn(RBAC_DEFAULT_WORKSPACE_ID);
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
        Mockito.when(this.workspaceUtils.getDefaultWorkspaceId(orgId)).thenReturn(returningWorkspaceId);
    }

    /**
     * Mocks the {@link CheckClient} so that it returns an authorized response
     * for the given permission.
     * @param subjectUsername the subject's name as sent in the "x-rh-identity"
     *                        header.
     * @param permission the permission that will be checked in the handler.
     * @param resourceType the resource type against which the permission will
     *                     be checked.
     * @param resourceId the resource's identifier.
     */
    public void mockKesselPermission(final String subjectUsername, final KesselPermission permission, final ResourceType resourceType, final String resourceId) {
        this.mockKesselPermission(subjectUsername, permission, resourceType, resourceId, CheckResponse.Allowed.ALLOWED_TRUE);
    }

    /**
     * Mocks the {@link CheckClient} so that it returns an authorized response
     * for the given permission.
     * @param subjectUsername the subject's name as sent in the "x-rh-identity"
     *                        header.
     * @param permission the permission that will be checked in the handler.
     * @param resourceType the resource type against which the permission will
     *                     be checked.
     * @param resourceId the resource's identifier.
     * @param allowedResponse the returned response from Kessel.
     */
    public void mockKesselPermission(final String subjectUsername, final KesselPermission permission, final ResourceType resourceType, final String resourceId, final CheckResponse.Allowed allowedResponse) {
        if (!this.backendConfig.isKesselRelationsEnabled(anyString())) {
            return;
        }

        Mockito.when(
            this.checkClient.check(
                CheckRequest.newBuilder()
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
                                    .setType(ObjectType.newBuilder().setName(KesselAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE).setNamespace(KesselAuthorization.KESSEL_RBAC_NAMESPACE).build())
                                    .setId(backendConfig.getKesselDomain() + "/" + subjectUsername)
                                    .build()
                            ).build()
                    ).build()
            )
        ).thenReturn(
            CheckResponse
                .newBuilder()
                .setAllowed(allowedResponse)
                .build()
        );

        KesselInventoryResourceType krs = (resourceType == ResourceType.WORKSPACE) ? KesselInventoryResourceType.WORKSPACE : KesselInventoryResourceType.INTEGRATION;
        org.project_kessel.api.inventory.v1beta1.authz.CheckResponse.Allowed kInventoryAllowedResponse = (allowedResponse == CheckResponse.Allowed.ALLOWED_TRUE) ? org.project_kessel.api.inventory.v1beta1.authz.CheckResponse.Allowed.ALLOWED_TRUE : org.project_kessel.api.inventory.v1beta1.authz.CheckResponse.Allowed.ALLOWED_FALSE;
        Mockito.when(
            this.kesselCheckClient.Check(
                org.project_kessel.api.inventory.v1beta1.authz.CheckRequest.newBuilder()
                    .setParent(
                        org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                            .setType(krs.getKesselObjectType())
                            .setId(resourceId)
                            .build()
                    )
                    .setRelation(permission.getKesselPermissionName())
                    .setSubject(
                        org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                            .setSubject(
                                org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                                    .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder().setName(KesselInventoryAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE).setNamespace(KesselInventoryAuthorization.KESSEL_RBAC_NAMESPACE).build())
                                    .setId(backendConfig.getKesselDomain() + "/" + subjectUsername)
                                    .build()
                            ).build()
                    ).build()
            )
        ).thenReturn(
            org.project_kessel.api.inventory.v1beta1.authz.CheckResponse
                .newBuilder()
                .setAllowed(kInventoryAllowedResponse)
                .build()
        );

        org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse.Allowed checkForUpdateInventoryAllowedResponse = (allowedResponse == CheckResponse.Allowed.ALLOWED_TRUE) ? org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse.Allowed.ALLOWED_TRUE : org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse.Allowed.ALLOWED_FALSE;
        Mockito.when(
            this.kesselCheckClient.CheckForUpdate(
                org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateRequest.newBuilder()
                    .setParent(
                        org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                            .setType(krs.getKesselObjectType())
                            .setId(resourceId)
                            .build()
                    )
                    .setRelation(permission.getKesselPermissionName())
                    .setSubject(
                        org.project_kessel.api.inventory.v1beta1.authz.SubjectReference.newBuilder()
                            .setSubject(
                                org.project_kessel.api.inventory.v1beta1.authz.ObjectReference.newBuilder()
                                    .setType(org.project_kessel.api.inventory.v1beta1.authz.ObjectType.newBuilder().setName(KesselInventoryAuthorization.KESSEL_IDENTITY_SUBJECT_TYPE).setNamespace(KesselInventoryAuthorization.KESSEL_RBAC_NAMESPACE).build())
                                    .setId(backendConfig.getKesselDomain() + "/" + subjectUsername)
                                    .build()
                            ).build()
                    ).build()
            )
        ).thenReturn(
            org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse
                .newBuilder()
                .setAllowed(checkForUpdateInventoryAllowedResponse)
                .build()
        );
    }

    /**
     * Mocks the {@link BackendConfig#isKesselRelationsEnabled(String)} so that it
     * returns the given boolean flag when asked, and also makes the {@link CheckClient}
     * return an "allowed" response when the flag is {@code true}.
     * @param isKesselRelationsEnabled is the Kessel relations enabled for the
     *                                 test?
     * @param isKesselInventoryUseForPermissionsChecksEnabled is the Kessel inventory
     *                                 enabled for the test?     *
     */
    public void mockKesselRelations(final boolean isKesselRelationsEnabled, final boolean isKesselInventoryUseForPermissionsChecksEnabled) {
        Mockito
            .when(this.backendConfig.isKesselRelationsEnabled(anyString()))
            .thenReturn(isKesselRelationsEnabled);

        Mockito
            .when(this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(anyString()))
            .thenReturn(isKesselInventoryUseForPermissionsChecksEnabled);

        if (!this.backendConfig.isKesselRelationsEnabled(anyString())) {
            return;
        }

        // Return a default domain for Kessel.
        Mockito
            .when(this.backendConfig.getKesselDomain())
            .thenReturn(DEFAULT_KESSEL_DOMAIN);

        // Default to an unauthorized response.
        Mockito
            .when(this.checkClient.check(Mockito.any()))
            .thenReturn(CheckResponse
                .newBuilder()
                .setAllowed(CheckResponse.Allowed.ALLOWED_FALSE)
                .build()
            );

        Mockito
            .when(this.kesselCheckClient.Check(Mockito.any()))
            .thenReturn(org.project_kessel.api.inventory.v1beta1.authz.CheckResponse
                .newBuilder()
                .setAllowed(org.project_kessel.api.inventory.v1beta1.authz.CheckResponse.Allowed.ALLOWED_FALSE)
                .build()
            );

        Mockito
            .when(this.kesselCheckClient.CheckForUpdate(Mockito.any()))
            .thenReturn(org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse
                .newBuilder()
                .setAllowed(org.project_kessel.api.inventory.v1beta1.authz.CheckForUpdateResponse.Allowed.ALLOWED_FALSE)
                .build()
            );
    }
}
