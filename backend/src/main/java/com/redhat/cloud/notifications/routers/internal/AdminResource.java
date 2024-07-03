package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.StuffHolder;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.RbacRaw;
import com.redhat.cloud.notifications.auth.rbac.RbacServer;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * Stuff around admin of the service and debugging
 */
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/admin")
public class AdminResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    @RestClient
    RbacServer rbacServer;

    @Inject
    @RestClient
    TemplateEngineClient templateEngine;

    @GET
    @Produces(APPLICATION_JSON)
    public Response debugRbac(@Context SecurityContext securityContext, @QueryParam("rhid") String rhid) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDebugRbac(rhid);
        } else {
            return this.legacyRBACDebugRbac(rhid);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response legacyRBACDebugRbac(final String rhid) {
        return this.internalDebugRbac(rhid);
    }

    public Response internalDebugRbac(final String rhid) {
        try {
            RbacRaw rbacRaw = rbacServer.getRbacInfo("notifications,integrations", rhid)
                .await().atMost(Duration.ofSeconds(2L));
            return Response.ok(rbacRaw.data).build();
        } catch (Exception e) {
            return Response.serverError().entity("Rbac call failed -- see logs").build();
        }
    }

    @Path("/status")
    @POST
    @Produces(TEXT_PLAIN)
    public Response setAdminDown(@Context SecurityContext securityContext, @QueryParam("status") Optional<String> status) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return internalSetAdminDown(status);
        } else {
            return legacyRBACSetAdminDown(status);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response legacyRBACSetAdminDown(final Optional<String> status) {
        return this.internalSetAdminDown(status);
    }

    public Response internalSetAdminDown(final Optional<String> status) {
        Response.ResponseBuilder builder;

        StuffHolder th = StuffHolder.getInstance();

        switch (status.orElse("ok")) {
            case "ok":
                th.setDegraded(false);
                th.setAdminDown(false);
                builder = Response.ok()
                        .entity("Reset state to ok");
                break;
            case "degraded":
                th.setDegraded(true);
                builder = Response.ok()
                        .entity("Set degraded state");
                break;
            case "admin-down":
                th.setAdminDown(true);
                builder = Response.ok()
                        .entity("Set admin down state");
                break;
            default:
                builder = Response.status(Response.Status.BAD_REQUEST)
                        .entity("Unknown status passed");
        }

        return builder.build();
    }

    // TODO NOTIF-484 Remove this method when the templates DB migration is finished.
    @DELETE
    @Path("/templates/migrate")
    public void deleteAllTemplates(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalDeleteAllTemplates();
        } else {
            this.legacyRBACDeleteAllTemplates();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void legacyRBACDeleteAllTemplates() {
        this.internalDeleteAllTemplates();
    }

    public void internalDeleteAllTemplates() {
        this.templateEngine.deleteAllTemplates();
    }

    // TODO NOTIF-484 Remove this method when the templates DB migration is finished.
    @PUT
    @Path("/templates/migrate")
    public void migrate(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalMigrate();
        } else {
            this.legacyRBACMigrate();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void legacyRBACMigrate() {
        this.internalMigrate();
    }

    public void internalMigrate() {
        this.templateEngine.migrate();
    }
}
