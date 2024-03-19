package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.StartupUtils;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.InternalRoleAccessRepository;
import com.redhat.cloud.notifications.db.repositories.StatusRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.SecurityContextUtil;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequest;
import com.redhat.cloud.notifications.routers.engine.DailyDigestService;
import com.redhat.cloud.notifications.routers.engine.ReplayService;
import com.redhat.cloud.notifications.routers.internal.models.AddApplicationRequest;
import com.redhat.cloud.notifications.routers.internal.models.RequestDefaultBehaviorGroupPropertyList;
import com.redhat.cloud.notifications.routers.internal.models.ServerInfo;
import com.redhat.cloud.notifications.routers.internal.models.UpdateApplicationRequest;
import com.redhat.cloud.notifications.routers.internal.models.dto.ApplicationDTO;
import com.redhat.cloud.notifications.routers.internal.models.transformer.ApplicationDTOTransformer;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;

import java.net.URI;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class InternalResource {

    private static final Pattern GIT_COMMIT_ID_PATTERN = Pattern.compile("git.commit.id.abbrev=([0-9a-f]{7})");

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    @RestClient
    DailyDigestService dailyDigestService;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    Environment environment;

    @Inject
    StatusRepository statusRepository;

    @Inject
    InternalRoleAccessRepository internalRoleAccessRepository;

    @Inject
    OApiFilter oApiFilter;

    @Inject
    SecurityContextUtil securityContextUtil;

    @Inject
    StartupUtils startupUtils;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    @RestClient
    ReplayService replayService;

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
            Log.infof("Git commit hash not found: %s", gitProperties);
            return "Git commit hash not found";
        }
    }

    @POST
    @Path("/replay")
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public void replay() {
        replayService.replay();
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
        return oApiFilter.serveOpenApi(OApiFilter.INTERNAL, null);
    }

    @POST
    @Path("/bundles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Bundle createBundle(@NotNull @Valid Bundle bundle) {
        return bundleRepository.createBundle(bundle);
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Bundle> getBundles() {
        // Return configured with types?
        return bundleRepository.getBundles();
    }

    @GET
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Bundle getBundle(@PathParam("bundleId") UUID bundleId) {
        Bundle bundle = bundleRepository.getBundle(bundleId);
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
        int rowCount = bundleRepository.updateBundle(bundleId, bundle);
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
    @TransactionConfiguration(timeout = 300)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleRepository.deleteBundle(bundleId);
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Application> getApplications(@PathParam("bundleId") UUID bundleId) {
        return bundleRepository.getApplications(bundleId);
    }

    @POST
    @Path("/applications")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ApplicationDTO createApplication(@Context SecurityContext sec, @NotNull @Valid AddApplicationRequest request) {
        securityContextUtil.hasPermissionForRole(sec, request.ownerRole);

        Application app = new Application();
        app.setBundleId(request.bundleId);
        app.setDisplayName(request.displayName);
        app.setName(request.name);
        app = applicationRepository.createApp(app);

        InternalRoleAccess internalRoleAccess = null;
        if (request.ownerRole != null && !request.ownerRole.isBlank()) {
            internalRoleAccess = new InternalRoleAccess();

            internalRoleAccess.setRole(request.ownerRole);
            internalRoleAccess.setApplicationId(app.getId());
            internalRoleAccess.setApplication(app);
            this.internalRoleAccessRepository.addAccess(internalRoleAccess);
        }

        return ApplicationDTOTransformer.toDTO(app, internalRoleAccess);
    }

    @GET
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ApplicationDTO getApplication(@PathParam("appId") UUID appId) {
        final Application app = applicationRepository.getApplication(appId);

        if (app == null) {
            throw new NotFoundException();
        } else {
            final InternalRoleAccess internalRoleAccess = this.internalRoleAccessRepository.findOneByApplicationUUID(appId);

            return ApplicationDTOTransformer.toDTO(app, internalRoleAccess);
        }
    }

    @PUT
    @Path("/applications/{appId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response updateApplication(@Context SecurityContext sec, @PathParam("appId") UUID appId, @NotNull UpdateApplicationRequest uar) {
        // Make sure that the user has the permission to modify the application
        // or its permission.
        securityContextUtil.hasPermissionForApplication(sec, appId);

        // Attempt fetching the application.
        final Application application = this.applicationRepository.getApplication(appId);
        if (application == null) {
            return Response
                .status(HttpStatus.SC_NOT_FOUND)
                .entity("{\"error\": \"The application was not found\"}")
                .header("Content-Type", APPLICATION_JSON)
                .build();
        }

        // We have to set the bundle ID because otherwise the entity manager
        // throws a constraint violation error about the bundle ID being
        // empty.
        application.setBundleId(application.getBundle().getId());

        // Update the application's details.
        final String newApplicationName = uar.name;
        if (newApplicationName != null && !newApplicationName.isBlank()) {
            application.setName(newApplicationName);
        }

        final String newApplicationDisplayName = uar.displayName;
        if (newApplicationDisplayName != null && !newApplicationDisplayName.isBlank()) {
            application.setDisplayName(newApplicationDisplayName);
        }

        // Prepare the internal role to be updated.
        final InternalRoleAccess internalRoleAccess = new InternalRoleAccess();
        internalRoleAccess.setRole(uar.ownerRole);
        internalRoleAccess.setApplicationId(application.getId());
        internalRoleAccess.setApplication(application);

        // Update the application and its permission.
        this.applicationRepository.updateApplicationAndAccess(application, internalRoleAccess);

        return Response.ok().build();
    }

    @DELETE
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteApplication(@Context SecurityContext sec, @PathParam("appId") UUID appId) {
        return applicationRepository.deleteApplication(appId);
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<EventType> getEventTypes(@PathParam("appId") UUID appId) {
        return applicationRepository.getEventTypes(appId);
    }

    @POST
    @Path("/eventTypes")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public EventType createEventType(@Context SecurityContext sec, @NotNull @Valid EventType eventType) {
        securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
        return applicationRepository.createEventType(eventType);
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response updateEventType(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid EventType eventType) {
        securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
        int rowCount = applicationRepository.updateEventType(eventTypeId, eventType);
        if (rowCount == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            subscriptionRepository.resubscribeAllUsersIfNeeded(eventTypeId);
            return Response.ok().build();
        }
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/updateVisibility")
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response updateEventTypeVisibility(@PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid boolean isVisible) {
        int rowCount = applicationRepository.updateEventTypeVisibility(eventTypeId, isVisible);
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
        return applicationRepository.deleteEventTypeById(eventTypeId);
    }

    @PUT
    @Path("/status")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void setCurrentStatus(@NotNull @Valid CurrentStatus status) {
        statusRepository.setCurrentStatus(status);
    }

    @GET
    @Path("/behaviorGroups/default")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<BehaviorGroup> getDefaultBehaviorGroups() {
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findDefaults();
        endpointRepository.loadProperties(
                behaviorGroups
                        .stream()
                        .map(BehaviorGroup::getActions)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .map(BehaviorGroupAction::getEndpoint)
                        .collect(Collectors.toList())
        );
        return behaviorGroups;
    }

    @POST
    @Path("/behaviorGroups/default")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public BehaviorGroup createDefaultBehaviorGroup(@NotNull @Valid BehaviorGroup behaviorGroup) {
        return behaviorGroupRepository.createDefault(behaviorGroup);
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
        behaviorGroupRepository.updateDefault(behaviorGroup);
        return true;
    }

    @DELETE
    @Path("/behaviorGroups/default/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Deletes a default behavior group.")
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean deleteDefaultBehaviorGroup(@PathParam("id") UUID id) {
        return behaviorGroupRepository.deleteDefault(id);
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
            SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
            properties.setOnlyAdmins(p.isOnlyAdmins());
            properties.setIgnorePreferences(p.isIgnorePreferences());
            return endpointRepository.getOrCreateSystemSubscriptionEndpoint(null, null, properties, EMAIL_SUBSCRIPTION);
        }).collect(Collectors.toList());
        behaviorGroupRepository.updateDefaultBehaviorGroupActions(
                behaviorGroupId,
                endpoints.stream().distinct().map(Endpoint::getId).collect(Collectors.toList())
        );
        return Response.ok().build();
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
        boolean isSuccess = behaviorGroupRepository.linkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId);
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
        boolean isSuccess = behaviorGroupRepository.unlinkEventTypeDefaultBehavior(eventTypeId, behaviorGroupId);
        if (isSuccess) {
            return Response.ok().build();
        } else {
            return Response.status(BAD_REQUEST).build();
        }
    }

    @PUT
    @Path("/daily-digest/time-preference/{orgId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response saveDailyDigestTimePreference(@NotNull LocalTime expectedTime, @RestPath String orgId) {
        Log.infof("Update daily digest time preference form internal API, for orgId %s at %s", orgId, expectedTime);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
        return Response.ok().build();
    }

    @GET
    @Path("/daily-digest/time-preference/{orgId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response getDailyDigestTimePreference(@PathParam("orgId") String orgId) {
        Log.infof("Get daily digest time preference form internal API, for orgId %s", orgId);
        AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
        if (null != storedParameters) {
            return Response.ok(storedParameters.getScheduledExecutionTime()).build();
        } else {
            return Response.status(NOT_FOUND).build();
        }
    }

    /**
     * Sends a daily digest command to the engine via a REST request.
     * @param triggerDailyDigestRequest the settings of the digest.
     */
    @Consumes(APPLICATION_JSON)
    @POST
    @Path("/daily-digest/trigger")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public void triggerDailyDigest(@NotNull @Valid final TriggerDailyDigestRequest triggerDailyDigestRequest) {
        if (!this.environment.isLocal() && !this.environment.isStage()) {
            throw new BadRequestException("the daily digests can only be triggered in the stage environment");
        }

        if (
            !this.applicationRepository.applicationBundleExists(
                triggerDailyDigestRequest.getApplicationName(),
                triggerDailyDigestRequest.getBundleName()
            )
        ) {
            throw new BadRequestException("unable to find the specified application — bundle combination");
        }

        this.dailyDigestService.triggerDailyDigest(triggerDailyDigestRequest);
    }
}
