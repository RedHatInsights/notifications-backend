package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.StartupUtils;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BehaviorGroupResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.StatusResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import com.redhat.cloud.notifications.routers.internal.models.RequestDefaultBehaviorGroupPropertyList;
import com.redhat.cloud.notifications.routers.internal.models.ServerInfo;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateRequest;
import com.redhat.cloud.notifications.routers.models.RenderEmailTemplateResponse;
import com.redhat.cloud.notifications.templates.TemplateEngineClient;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class InternalService {

    private static final Logger LOGGER = Logger.getLogger(InternalService.class);
    private static final Pattern GIT_COMMIT_ID_PATTERN = Pattern.compile("git.commit.id.abbrev=([0-9a-f]{7})");

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources appResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    StatusResources statusResources;

    @Inject
    OApiFilter oApiFilter;

    @Inject
    SecurityContextUtil securityContextUtil;

    @Inject
    @RestClient
    TemplateEngineClient templateEngineClient;

    @Inject
    StartupUtils startupUtils;

    // This endpoint is used during the IQE tests to determine which version of the code is tested.
    @GET
    @Path("/version")
    @PermitAll
    public String getVersion() {
        String gitProperties = startupUtils.readGitProperties();
        Matcher m = GIT_COMMIT_ID_PATTERN.matcher(gitProperties);
        if (m.matches()) {
            return m.group(1);
        } else {
            LOGGER.infof("Git commit hash not found: %s", gitProperties);
            return "Git commit hash not found";
        }
    }

    @GET
    @Path("/serverInfo")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ServerInfo getServerInfo() {
        ServerInfo info = new ServerInfo();
        String environmentName = System.getenv("ENV_NAME");

        info.environment = ServerInfo.Environment.valueOf(environmentName == null ? "LOCAL_SERVER" : environmentName.toUpperCase());

        return info;
    }

    @GET
    @Path("/")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public void httpRoot() {
        throw new RedirectionException(Response.Status.OK, URI.create("index.html"));
    }

    @GET
    @Path("/openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public String serveInternalOpenAPI() {
        return oApiFilter.serveOpenApi(OApiFilter.INTERNAL);
    }

    @POST
    @Path("/bundles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Bundle createBundle(@NotNull @Valid Bundle bundle) {
        return bundleResources.createBundle(bundle);
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Bundle> getBundles() {
        // Return configured with types?
        return bundleResources.getBundles();
    }

    @GET
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Bundle getBundle(@PathParam("bundleId") UUID bundleId) {
        Bundle bundle = bundleResources.getBundle(bundleId);
        if (bundle == null) {
            throw new NotFoundException();
        } else {
            return bundle;
        }
    }

    @PUT
    @Path("/bundles/{bundleId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response updateBundle(@PathParam("bundleId") UUID bundleId, @NotNull @Valid Bundle bundle) {
        int rowCount = bundleResources.updateBundle(bundleId, bundle);
        if (rowCount == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok().build();
        }
    }

    @DELETE
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.deleteBundle(bundleId);
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Application> getApplications(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.getApplications(bundleId);
    }

    @POST
    @Path("/applications")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Application createApplication(@NotNull @Valid Application app) {
        return appResources.createApp(app);
    }

    @GET
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Application getApplication(@PathParam("appId") UUID appId) {
        Application app = appResources.getApplication(appId);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return app;
        }
    }

    @PUT
    @Path("/applications/{appId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response updateApplication(@Context SecurityContext sec, @PathParam("appId") UUID appId, @NotNull @Valid Application app) {
        securityContextUtil.hasPermissionForApplication(sec, appId);
        int rowCount = appResources.updateApplication(appId, app);
        if (rowCount == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok().build();
        }
    }

    @DELETE
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteApplication(@Context SecurityContext sec, @PathParam("appId") UUID appId) {
        return appResources.deleteApplication(appId);
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<EventType> getEventTypes(@PathParam("appId") UUID appId) {
        return appResources.getEventTypes(appId);
    }

    @POST
    @Path("/eventTypes")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public EventType createEventType(@Context SecurityContext sec, @NotNull @Valid EventType eventType) {
        securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
        return appResources.createEventType(eventType);
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response updateEventType(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid EventType eventType) {
        securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
        int rowCount = appResources.updateEventType(eventTypeId, eventType);
        if (rowCount == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok().build();
        }
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}")
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public boolean deleteEventType(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId) {
        securityContextUtil.hasPermissionForEventType(sec, eventTypeId);
        return appResources.deleteEventTypeById(eventTypeId);
    }

    @PUT
    @Path("/status")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void setCurrentStatus(@NotNull @Valid CurrentStatus status) {
        statusResources.setCurrentStatus(status);
    }

    @POST
    @Path("/templates/email/render")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", content = {
                    @Content(schema = @Schema(title = "RenderEmailTemplateResponseSuccess", implementation = RenderEmailTemplateResponse.Success.class))
            }),
            @APIResponse(responseCode = "400", content = {
                    @Content(schema = @Schema(title = "RenderEmailTemplateResponseError", implementation = RenderEmailTemplateResponse.Error.class))
            })
    })
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response renderEmailTemplate(@NotNull @Valid RenderEmailTemplateRequest renderEmailTemplateRequest) {
        try {
            return templateEngineClient.render(renderEmailTemplateRequest);
        } catch (BadRequestException e) {
            // The following line is required to forward the HTTP 400 error message.
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/behaviorGroups/default")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<BehaviorGroup> getDefaultBehaviorGroups() {
        return behaviorGroupResources.findDefaults();
    }

    @POST
    @Path("/behaviorGroups/default")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public BehaviorGroup createDefaultBehaviorGroup(@NotNull @Valid BehaviorGroup behaviorGroup) {
        return behaviorGroupResources.createDefault(behaviorGroup);
    }

    @PUT
    @Path("/behaviorGroups/default/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update a default behavior group.")
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean updateDefaultBehaviorGroup(@PathParam("id") UUID id, @NotNull @Valid BehaviorGroup behaviorGroup) {
        behaviorGroup.setId(id);
        return behaviorGroupResources.updateDefault(behaviorGroup);
    }

    @DELETE
    @Path("/behaviorGroups/default/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Deletes a default behavior group.")
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteDefaultBehaviorGroup(@PathParam("id") UUID id) {
        return behaviorGroupResources.deleteDefault(id);
    }

    @PUT
    @Path("/behaviorGroups/default/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of actions of a default behavior group.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response updateDefaultBehaviorGroupActions(@PathParam("behaviorGroupId") UUID behaviorGroupId, List<RequestDefaultBehaviorGroupPropertyList> propertiesList) {
        if (propertiesList == null) {
            throw new BadRequestException("The request body must contain a list of EmailSubscriptionProperties");
        }

        if (propertiesList.size() != propertiesList.stream().distinct().count()) {
            throw new BadRequestException("The list of EmailSubscriptionProperties should not contain duplicates");
        }

        List<Endpoint> endpoints = propertiesList.stream().map(p -> {
            EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
            properties.setOnlyAdmins(p.isOnlyAdmins());
            properties.setIgnorePreferences(p.isIgnorePreferences());
            return endpointResources.getOrCreateEmailSubscriptionEndpoint(null, properties);
        }).collect(Collectors.toList());
        Response.Status status = behaviorGroupResources.updateDefaultBehaviorGroupActions(
                behaviorGroupId,
                endpoints.stream().distinct().map(Endpoint::getId).collect(Collectors.toList())
        );
        return Response.status(status).build();
    }

    @PUT
    @Path("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Links the default behavior group to the event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response linkDefaultBehaviorToEventType(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        securityContextUtil.hasPermissionForEventType(sec, eventTypeId);
        boolean isSuccess = behaviorGroupResources.linkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId);
        if (isSuccess) {
            return Response.ok().build();
        } else {
            return Response.status(BAD_REQUEST).build();
        }
    }

    @DELETE
    @Path("/behaviorGroups/default/{behaviorGroupId}/eventType/{eventTypeId}")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Unlinks the default behavior group from the event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response unlinkDefaultBehaviorToEventType(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        securityContextUtil.hasPermissionForEventType(sec, eventTypeId);
        boolean isSuccess = behaviorGroupResources.unlinkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId);
        if (isSuccess) {
            return Response.ok().build();
        } else {
            return Response.status(BAD_REQUEST).build();
        }
    }
}
