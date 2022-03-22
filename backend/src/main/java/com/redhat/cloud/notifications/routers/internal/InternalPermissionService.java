package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.InternalRoleAccessResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.routers.internal.models.AddAccessRequest;
import com.redhat.cloud.notifications.routers.internal.models.InternalApplicationUserPermission;
import com.redhat.cloud.notifications.routers.internal.models.InternalUserPermissions;
import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/access")
public class InternalPermissionService {

    @Inject
    InternalRoleAccessResources internalRoleAccessResources;

    @Inject
    ApplicationResources applicationResources;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER) // Overrides admin permission
    public InternalUserPermissions getPermissions() {
        InternalUserPermissions permissions = new InternalUserPermissions();
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

        List<InternalRoleAccess> accessList = internalRoleAccessResources.getByRoles(roles);

        for (InternalRoleAccess access : accessList) {
            permissions.addApplication(access.getApplicationId(), access.getApplication().getDisplayName());
        }

        return permissions;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InternalApplicationUserPermission> getAccessList() {
        List<InternalRoleAccess> accessList = internalRoleAccessResources.getAll();
        Set<UUID> applicationIds = accessList.stream().map(InternalRoleAccess::getApplicationId).collect(Collectors.toSet());
        Map<UUID, String> applications = applicationResources
                .getApplications(applicationIds)
                .stream()
                .collect(Collectors.toMap(Application::getId, Application::getDisplayName));

        return accessList.stream().map(access -> {
            InternalApplicationUserPermission permission = new InternalApplicationUserPermission();
            permission.applicationDisplayName = applications.get(access.getApplicationId());
            permission.applicationId = access.getApplicationId().toString();
            permission.role =  access.getRole();
            return permission;
        }).collect(Collectors.toList());
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InternalRoleAccess addAccess(@Valid AddAccessRequest addAccessRequest) {
        InternalRoleAccess access = new InternalRoleAccess();
        Application application = applicationResources.getApplication(addAccessRequest.applicationId);
        access.setApplicationId(addAccessRequest.applicationId);
        access.setRole(addAccessRequest.role);
        access.setApplication(application);
        return internalRoleAccessResources.addAccess(access);
    }

    @DELETE
    @Path("/{internalRoleAccessId}")
    public void deleteAccess(@Valid @PathParam("internalRoleAccessId") UUID internalRoleAccessId) {
        internalRoleAccessResources.removeAccess(internalRoleAccessId);
    }

}
