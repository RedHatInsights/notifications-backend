package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.InternalRoleAccessRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.routers.internal.models.AddAccessRequest;
import com.redhat.cloud.notifications.routers.internal.models.InternalApplicationUserPermission;
import com.redhat.cloud.notifications.routers.internal.models.InternalUserPermissions;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;

@Path(API_INTERNAL + "/access")
public class InternalPermissionResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    InternalRoleAccessRepository internalRoleAccessRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)

    public InternalUserPermissions getPermissions(@Context SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetPermissions();
        } else {
            return this.legacyRBACGetPermissions();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public InternalUserPermissions legacyRBACGetPermissions() {
        return this.internalGetPermissions();
    }

    public InternalUserPermissions internalGetPermissions() {
        final InternalUserPermissions permissions = new InternalUserPermissions();
        if (securityIdentity.hasRole(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)) {
            permissions.setAdmin(true);
            return permissions;
        }

        String privateRolePrefix = InternalRoleAccess.INTERNAL_ROLE_PREFIX;

        Set<String> roles = securityIdentity
                .getRoles()
                .stream()
                .filter(s -> s.startsWith(privateRolePrefix))
                .map(s -> s.substring(privateRolePrefix.length()))
                .collect(Collectors.toSet());
        permissions.getRoles().addAll(roles);

        List<InternalRoleAccess> accessList = internalRoleAccessRepository.getByRoles(roles);

        for (InternalRoleAccess access : accessList) {
            permissions.addApplication(access.getApplicationId(), access.getApplication().getDisplayName());
        }

        return permissions;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InternalApplicationUserPermission> getAccessList(@Context SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetAccesList();
        } else {
            return this.legacyRBACInternalGetAccessList();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public List<InternalApplicationUserPermission> legacyRBACInternalGetAccessList() {
        return this.internalGetAccesList();
    }

    public List<InternalApplicationUserPermission> internalGetAccesList() {
        List<InternalRoleAccess> accessList = internalRoleAccessRepository.getAll();

        return accessList.stream().map(access -> {
            InternalApplicationUserPermission permission = new InternalApplicationUserPermission();
            permission.applicationDisplayName = access.getApplication().getDisplayName();
            permission.applicationId = access.getApplicationId();
            permission.role =  access.getRole();
            return permission;
        }).collect(Collectors.toList());
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InternalRoleAccess addAccess(@Context final SecurityContext securityContext, @Valid AddAccessRequest addAccessRequest) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalAddAccess(addAccessRequest);
        } else {
            return this.legacyRBACInternalAddAccess(addAccessRequest);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public InternalRoleAccess legacyRBACInternalAddAccess(final AddAccessRequest addAccessRequest) {
        return this.internalAddAccess(addAccessRequest);
    }

    public InternalRoleAccess internalAddAccess(final AddAccessRequest addAccessRequest) {
        InternalRoleAccess access = new InternalRoleAccess();
        Application application = applicationRepository.getApplication(addAccessRequest.applicationId);
        access.setApplicationId(addAccessRequest.applicationId);
        access.setRole(addAccessRequest.role);
        access.setApplication(application);
        return internalRoleAccessRepository.addAccess(access);
    }

    @DELETE
    @Path("/{internalRoleAccessId}")
    public void deleteAccess(@Context final SecurityContext securityContext, @Valid @PathParam("internalRoleAccessId") UUID internalRoleAccessId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalDeleteAccess(internalRoleAccessId);
        } else {
            this.legacyRBACDeleteAccess(internalRoleAccessId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void legacyRBACDeleteAccess(final UUID internalRoleAccessId) {
        this.internalDeleteAccess(internalRoleAccessId);
    }

    public void internalDeleteAccess(final UUID internalRoleAccessId) {
        this.internalRoleAccessRepository.removeAccess(internalRoleAccessId);
    }

}
