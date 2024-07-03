package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.StartupUtils;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.WorkspacePermission;
import com.redhat.cloud.notifications.config.BackendConfig;
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
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
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
    BackendConfig backendConfig;

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
    KesselAuthorization kesselAuthorization;

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

    @GET
    @Path("/serverInfo")
    @Produces(APPLICATION_JSON)
    public ServerInfo getServerInfo(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetServerInfo();
        } else {
            return this.legacyRBACGetServerInfo();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ServerInfo legacyRBACGetServerInfo() {
        return this.internalGetServerInfo();
    }

    public ServerInfo internalGetServerInfo() {
        ServerInfo info = new ServerInfo();
        String environmentName = System.getenv("ENV_NAME");

        info.environment = ServerInfo.Environment.valueOf(environmentName == null ? "LOCAL_SERVER" : environmentName.toUpperCase());

        return info;
    }

    @GET
    @Path("/")
    public void httpRoot(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalHttpRoot();
        } else {
            this.legacyRBACHttpRoot();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public void legacyRBACHttpRoot() {
        this.internalHttpRoot();
    }

    public void internalHttpRoot() {
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
    public Bundle createBundle(@Context final SecurityContext securityContext, @NotNull @Valid Bundle bundle) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateBundle(bundle);
        } else {
            return this.legacyRBACCreateBundle(bundle);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Bundle legacyRBACCreateBundle(final Bundle bundle) {
        return this.internalCreateBundle(bundle);
    }

    public Bundle internalCreateBundle(final Bundle bundle) {
        return this.bundleRepository.createBundle(bundle);
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    public List<Bundle> getBundles(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetBundles();
        } else {
            return this.legacyRBACGetBundles();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Bundle> legacyRBACGetBundles() {
        return this.internalGetBundles();
    }

    public List<Bundle> internalGetBundles() {
        // Return configured with types?
        return this.bundleRepository.getBundles();
    }

    @GET
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    public Bundle getBundle(@Context final SecurityContext securityContext, @PathParam("bundleId") UUID bundleId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetBundle(bundleId);
        } else {
            return this.legacyRBACGetBundle(bundleId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Bundle legacyRBACGetBundle(final UUID bundleId) {
        return this.internalGetBundle(bundleId);
    }

    public Bundle internalGetBundle(final UUID bundleId) {
        Bundle bundle = this.bundleRepository.getBundle(bundleId);
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
    @TransactionConfiguration(timeout = 300)
    /*
     * We need to increase the transaction timeout for this method because it involves
     * an update of 'event' table records based on 'bundleId' column which is not indexed.
     */
    public Response updateBundle(@Context final SecurityContext securityContext, @PathParam("bundleId") UUID bundleId, @NotNull @Valid Bundle bundle) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateBundle(bundleId, bundle);
        } else {
            return this.legacyRBACUpdateBundle(bundleId, bundle);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateBundle(final UUID bundleId, final Bundle bundle) {
        return this.internalUpdateBundle(bundleId, bundle);
    }

    public Response internalUpdateBundle(final UUID bundleId, final Bundle bundle) {
        int rowCount = this.bundleRepository.updateBundle(bundleId, bundle);
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
    /*
     * We need to increase the transaction timeout for this method because it involves
     * a full scan of 'event' table based on 'bundleId' column which is not indexed.
     */
    public boolean deleteBundle(@Context final SecurityContext securityContext, @PathParam("bundleId") UUID bundleId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteBundle(bundleId);
        } else {
            return this.legacyRBACDeleteBundle(bundleId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteBundle(final UUID bundleId) {
        return this.internalDeleteBundle(bundleId);
    }

    public boolean internalDeleteBundle(final UUID bundleId) {
        return this.bundleRepository.deleteBundle(bundleId);
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    @Produces(APPLICATION_JSON)
    public List<Application> getApplications(@Context final SecurityContext securityContext, @PathParam("bundleId") UUID bundleId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetApplications(bundleId);
        } else {
            return this.legacyRBACGetApplications(bundleId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<Application> legacyRBACGetApplications(final UUID bundleId) {
        return this.internalGetApplications(bundleId);
    }

    public List<Application> internalGetApplications(final UUID bundleId) {
        return this.bundleRepository.getApplications(bundleId);
    }

    @POST
    @Path("/applications")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public ApplicationDTO createApplication(@Context SecurityContext securityContext, @NotNull @Valid AddApplicationRequest request) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateApplication(securityContext, request);
        } else {
            return this.legacyRBACCreateApplication(securityContext, request);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ApplicationDTO legacyRBACCreateApplication(final SecurityContext securityContext, final AddApplicationRequest request) {
        return this.internalCreateApplication(securityContext, request);
    }

    public ApplicationDTO internalCreateApplication(final SecurityContext securityContext, final AddApplicationRequest request) {
        this.securityContextUtil.hasPermissionForRole(securityContext, request.ownerRole);

        Application app = new Application();
        app.setBundleId(request.bundleId);
        app.setDisplayName(request.displayName);
        app.setName(request.name);
        app = this.applicationRepository.createApp(app);

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
    public ApplicationDTO getApplication(@Context final SecurityContext securityContext, @PathParam("appId") UUID appId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetApplication(appId);
        } else {
            return this.legacyRBACGetApplication(appId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public ApplicationDTO legacyRBACGetApplication(final UUID appId) {
        return this.internalGetApplication(appId);
    }

    public ApplicationDTO internalGetApplication(final UUID appId) {
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
    public Response updateApplication(@Context SecurityContext securityContext, @PathParam("appId") UUID appId, @NotNull UpdateApplicationRequest uar) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateApplication(securityContext, appId, uar);
        } else {
            return this.legacyRBACUpdateApplication(securityContext, appId, uar);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response legacyRBACUpdateApplication(final SecurityContext securityContext, final UUID appId, final UpdateApplicationRequest uar) {
        return this.internalUpdateApplication(securityContext, appId, uar);
    }

    public Response internalUpdateApplication(final SecurityContext securityContext, final UUID appId, final UpdateApplicationRequest uar) {
        // Make sure that the user has the permission to modify the application
        // or its permission.
        this.securityContextUtil.hasPermissionForApplication(securityContext, appId);

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
    @TransactionConfiguration(timeout = 300)
    /*
     * We need to increase the transaction timeout for this method because it involves
     * a cascade delete action on 'event' table based on 'applicationId' column which is not indexed.
     */
    public boolean deleteApplication(@Context final SecurityContext securityContext, @PathParam("appId") UUID appId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteApplication(appId);
        } else {
            return this.legacyRBACDeleteApplication(appId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteApplication(final UUID appId) {
        return this.internalDeleteApplication(appId);
    }

    public boolean internalDeleteApplication(final UUID appId) {
        return this.applicationRepository.deleteApplication(appId);
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    @Produces(APPLICATION_JSON)
    public List<EventType> getEventTypes(@Context final SecurityContext securityContext, @PathParam("appId") UUID appId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetEventTypes(appId);
        } else {
            return this.legacyRBACGetEventTypes(appId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<EventType> legacyRBACGetEventTypes(final UUID appId) {
        return this.internalGetEventTypes(appId);
    }

    public List<EventType> internalGetEventTypes(final UUID appId) {
        return this.applicationRepository.getEventTypes(appId);
    }

    @POST
    @Path("/eventTypes")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    public EventType createEventType(@Context SecurityContext securityContext, @NotNull @Valid EventType eventType) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateEventType(securityContext, eventType);
        } else {
            return this.legacyRBACCreateEventType(securityContext, eventType);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public EventType legacyRBACCreateEventType(final SecurityContext securityContext, final EventType eventType) {
        return this.internalCreateEventType(securityContext, eventType);
    }

    public EventType internalCreateEventType(final SecurityContext securityContext, final EventType eventType) {
        this.securityContextUtil.hasPermissionForApplication(securityContext, eventType.getApplicationId());

        return applicationRepository.createEventType(eventType);
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Transactional
    @TransactionConfiguration(timeout = 300)
    /*
     * We need to increase the transaction timeout for this method because it involves
     * an update of 'event' table records based on 'eventTypeId' column which is not indexed.
     */
    public Response updateEventType(@Context SecurityContext securityContext, @PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid EventType eventType) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return internalUpdateEventType(securityContext, eventTypeId, eventType);
        } else {
            return legacyRBACUpdateEventType(securityContext, eventTypeId, eventType);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response legacyRBACUpdateEventType(final SecurityContext securityContext, final UUID eventTypeId, final EventType eventType) {
        return this.internalUpdateEventType(securityContext, eventTypeId, eventType);
    }

    public Response internalUpdateEventType(final SecurityContext sec, final UUID eventTypeId, final EventType eventType) {
        this.securityContextUtil.hasPermissionForApplication(sec, eventType.getApplicationId());
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
    public Response updateEventTypeVisibility(@Context final SecurityContext securityContext, @PathParam("eventTypeId") UUID eventTypeId, @NotNull @Valid boolean isVisible) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateEventTypeVisibility(eventTypeId, isVisible);
        } else {
            return this.legacyRBACUpdateEventTypeVisibility(eventTypeId, isVisible);
        }
    }

    @RolesAllowed(RBAC_INTERNAL_ADMIN)
    public Response legacyRBACUpdateEventTypeVisibility(final UUID eventTypeId, final boolean isVisible) {
        return this.internalUpdateEventTypeVisibility(eventTypeId, isVisible);
    }

    public Response internalUpdateEventTypeVisibility(final UUID eventTypeId, final boolean isVisible) {
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
    @TransactionConfiguration(timeout = 300)
    /*
     * We need to increase the transaction timeout for this method because it involves
     * a full scan of 'event' table based on 'eventTypeId' column which is not indexed.
     */
    public boolean deleteEventType(@Context SecurityContext securityContext, @PathParam("eventTypeId") UUID eventTypeId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteEventType(securityContext, eventTypeId);
        } else {
            return this.legacyRBACDeleteEventType(securityContext, eventTypeId);
        }
    }

    public boolean legacyRBACDeleteEventType(final SecurityContext securityContext, final UUID eventTypeId) {
        return this.internalDeleteEventType(securityContext, eventTypeId);
    }

    public boolean internalDeleteEventType(final SecurityContext securityContext, final UUID eventTypeId) {
        this.securityContextUtil.hasPermissionForEventType(securityContext, eventTypeId);
        return applicationRepository.deleteEventTypeById(eventTypeId);
    }

    @PUT
    @Path("/status")
    @Consumes(APPLICATION_JSON)
    @Transactional
    public void setCurrentStatus(@Context final SecurityContext securityContext, @NotNull @Valid CurrentStatus status) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalSetCurrentStatus(status);
        } else {
            this.legacyRBACSetCurrentStatus(status);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public void legacyRBACSetCurrentStatus(final CurrentStatus status) {
        this.internalSetCurrentStatus(status);
    }

    public void internalSetCurrentStatus(final CurrentStatus status) {
        this.statusRepository.setCurrentStatus(status);
    }

    @GET
    @Path("/behaviorGroups/default")
    @Produces(APPLICATION_JSON)
    public List<BehaviorGroup> getDefaultBehaviorGroups(@Context final SecurityContext securityContext) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetDefaultBehaviorGroups();
        } else {
            return this.legacyRBACGetDefaultBehaviorGroups();
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public List<BehaviorGroup> legacyRBACGetDefaultBehaviorGroups() {
        return this.internalGetDefaultBehaviorGroups();
    }

    public List<BehaviorGroup> internalGetDefaultBehaviorGroups() {
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
    public BehaviorGroup createDefaultBehaviorGroup(@Context final SecurityContext securityContext, @NotNull @Valid BehaviorGroup behaviorGroup) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateDefaultBehaviorGroup(behaviorGroup);
        } else {
            return this.legacyRBACCreateDefaultBehaviorGroup(behaviorGroup);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public BehaviorGroup legacyRBACCreateDefaultBehaviorGroup(final BehaviorGroup behaviorGroup) {
        return this.internalCreateDefaultBehaviorGroup(behaviorGroup);
    }

    public BehaviorGroup internalCreateDefaultBehaviorGroup(final BehaviorGroup behaviorGroup) {
        return this.behaviorGroupRepository.createDefault(behaviorGroup);
    }

    @PUT
    @Path("/behaviorGroups/default/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update a default behavior group.")
    @Transactional
    public boolean updateDefaultBehaviorGroup(@Context final SecurityContext securityContext, @PathParam("id") UUID id, @NotNull @Valid BehaviorGroup behaviorGroup) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUpdateDefaultBehaviorGroup(id, behaviorGroup);
        } else {
            return this.legacyRBACUpdateDefaultBehaviorGroup(id, behaviorGroup);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACUpdateDefaultBehaviorGroup(final UUID id, final BehaviorGroup behaviorGroup) {
        return this.internalUpdateDefaultBehaviorGroup(id, behaviorGroup);
    }

    public boolean internalUpdateDefaultBehaviorGroup(final UUID id, final BehaviorGroup behaviorGroup) {
        behaviorGroup.setId(id);
        this.behaviorGroupRepository.updateDefault(behaviorGroup);
        return true;
    }

    @DELETE
    @Path("/behaviorGroups/default/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Deletes a default behavior group.")
    @Transactional
    public boolean deleteDefaultBehaviorGroup(@Context final SecurityContext securityContext, @PathParam("id") UUID id) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDeleteDefaultBehaviorGroup(id);
        } else {
            return this.legacyRBACDeleteDefaultBehaviorGroup(id);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public boolean legacyRBACDeleteDefaultBehaviorGroup(final UUID id) {
        return this.internalDeleteDefaultBehaviorGroup(id);
    }

    public boolean internalDeleteDefaultBehaviorGroup(final UUID id) {
        return this.behaviorGroupRepository.deleteDefault(id);
    }

    @PUT
    @Path("/behaviorGroups/default/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of actions of a default behavior group.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateDefaultBehaviorGroupActions(@Context final SecurityContext securityContext, @PathParam("behaviorGroupId") UUID behaviorGroupId, List<RequestDefaultBehaviorGroupPropertyList> propertiesList) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalDefaultBehaviorGroupActions(behaviorGroupId, propertiesList);
        } else {
            return this.legacyRBACDefaultBehaviorGroupActions(behaviorGroupId, propertiesList);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response legacyRBACDefaultBehaviorGroupActions(final UUID behaviorGroupId, final List<RequestDefaultBehaviorGroupPropertyList> propertiesList) {
        return this.internalDefaultBehaviorGroupActions(behaviorGroupId, propertiesList);
    }

    public Response internalDefaultBehaviorGroupActions(final UUID behaviorGroupId, final List<RequestDefaultBehaviorGroupPropertyList> propertiesList) {
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
    public Response linkDefaultBehaviorToEventType(@Context SecurityContext securityContext, @PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalLinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
        } else {
            return this.legacyRBACLinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response legacyRBACLinkDefaultBehaviorToEventType(final SecurityContext securityContext, final UUID behaviorGroupId, final UUID eventTypeId) {
        return this.internalLinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
    }

    public Response internalLinkDefaultBehaviorToEventType(final SecurityContext securityContext, final UUID behaviorGroupId, final UUID eventTypeId) {
        this.securityContextUtil.hasPermissionForEventType(securityContext, eventTypeId);
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
    public Response unlinkDefaultBehaviorToEventType(@Context SecurityContext securityContext, @PathParam("behaviorGroupId") UUID behaviorGroupId, @PathParam("eventTypeId") UUID eventTypeId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalUnlinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
        } else {
            return this.legacyRBACUnlinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response legacyRBACUnlinkDefaultBehaviorToEventType(final SecurityContext securityContext, final UUID behaviorGroupId, final UUID eventTypeId) {
        return this.internalUnlinkDefaultBehaviorToEventType(securityContext, behaviorGroupId, eventTypeId);
    }

    public Response internalUnlinkDefaultBehaviorToEventType(final SecurityContext securityContext, final UUID behaviorGroupId, final UUID eventTypeId) {
        this.securityContextUtil.hasPermissionForEventType(securityContext, eventTypeId);
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
    public Response saveDailyDigestTimePreference(@Context final SecurityContext securityContext, @NotNull LocalTime expectedTime, @RestPath String orgId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_ADMINISTRATOR, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalSaveDailyDigestTimePreference(expectedTime, orgId);
        } else {
            return this.legacyRBACSaveDailyDigestTimePreference(expectedTime, orgId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public Response legacyRBACSaveDailyDigestTimePreference(final LocalTime expectedTime, final String orgId) {
        return this.internalSaveDailyDigestTimePreference(expectedTime, orgId);
    }

    public Response internalSaveDailyDigestTimePreference(final LocalTime expectedTime, final String orgId) {
        Log.infof("Update daily digest time preference form internal API, for orgId %s at %s", orgId, expectedTime);
        this.aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
        return Response.ok().build();
    }

    @GET
    @Path("/daily-digest/time-preference/{orgId}")
    @Produces(APPLICATION_JSON)
    public Response getDailyDigestTimePreference(@Context final SecurityContext securityContext, @PathParam("orgId") String orgId) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalGetDailyDigestTimePreference(orgId);
        } else {
            return this.legacyRBACGetDailyDigestTimePreference(orgId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public Response legacyRBACGetDailyDigestTimePreference(final String orgId) {
        return this.internalGetDailyDigestTimePreference(orgId);
    }

    public Response internalGetDailyDigestTimePreference(final String orgId) {
        Log.infof("Get daily digest time preference form internal API, for orgId %s", orgId);
        final AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
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
    public void triggerDailyDigest(@Context final SecurityContext securityContext, @NotNull @Valid final TriggerDailyDigestRequest triggerDailyDigestRequest) {
        if (this.backendConfig.isKesselBackendEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(securityContext, WorkspacePermission.INTERNAL_USER, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            this.internalTriggerDailyDigest(triggerDailyDigestRequest);
        } else {
            this.legacyRBACTriggerDailyDigest(triggerDailyDigestRequest);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_USER)
    public void legacyRBACTriggerDailyDigest(final TriggerDailyDigestRequest triggerDailyDigestRequest) {
        this.internalTriggerDailyDigest(triggerDailyDigestRequest);
    }

    public void internalTriggerDailyDigest(final TriggerDailyDigestRequest triggerDailyDigestRequest) {
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
