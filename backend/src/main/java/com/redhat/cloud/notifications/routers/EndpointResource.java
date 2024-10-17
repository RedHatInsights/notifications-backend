package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAssets;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rbac.RbacGroupValidator;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.v1.ApplicationDTO;
import com.redhat.cloud.notifications.models.dto.v1.BundleDTO;
import com.redhat.cloud.notifications.models.dto.v1.CommonMapper;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointResource {

    public static final String DEPRECATED_SLACK_CHANNEL_ERROR = "The channel field is deprecated";
    public static final String HTTPS_ENDPOINT_SCHEME_REQUIRED = "The endpoint URL must start with \"https\"";
    public static final String UNSUPPORTED_ENDPOINT_TYPE = "Unsupported endpoint type";
    public static final String REDACTED_CREDENTIAL = "*****";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    CommonMapper commonMapper;

    @Inject
    @RestClient
    EndpointTestService endpointTestService;

    @Inject
    KesselAssets kesselAssets;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    RbacGroupValidator rbacGroupValidator;

    @Inject
    BackendConfig backendConfig;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    /**
     * Used to create the secrets in Sources and update the endpoint's properties' IDs.
     */
    @Inject
    SecretUtils secretUtils;

    @Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
    static class V1 extends EndpointResource {

        @GET
        @Path("/{id}/history")
        @Produces(APPLICATION_JSON)
        @Parameters({
            @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page, if not specified or 0 is used, returns a maximum of " + MAX_NOTIFICATION_HISTORY_RESULTS + " elements.",
                schema = @Schema(type = SchemaType.INTEGER)
                ),
            @Parameter(
                name = "pageNumber",
                in = ParameterIn.QUERY,
                description = "Page number. Starts at first page (0), if not specified starts at first page.",
                schema = @Schema(type = SchemaType.INTEGER)
                ),
            @Parameter(
                name = "includeDetail",
                description = "Include the detail in the reply",
                schema = @Schema(type = SchemaType.BOOLEAN)
                )
        })
        public List<NotificationHistoryDTO> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @BeanParam Query query) {
            if (this.backendConfig.isKesselRelationsEnabled()) {
                this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.VIEW_HISTORY, id);

                return this.internalGetEndpointHistory(sec, id, includeDetail, query);
            } else {
                return this.legacyRBACGetEndpointHistory(sec, id, includeDetail, query);
            }
        }

        @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
        protected List<NotificationHistoryDTO> legacyRBACGetEndpointHistory(final SecurityContext securityContext, final UUID id, final Boolean includeDetail, final Query query) {
            return this.internalGetEndpointHistory(securityContext, id, includeDetail, query);
        }

        protected List<NotificationHistoryDTO> internalGetEndpointHistory(final SecurityContext securityContext, final UUID id, final Boolean includeDetail, @Valid final Query query) {
            // TODO We need globally limitations (Paging support and limits etc)
            String orgId = getOrgId(securityContext);
            boolean doDetail = includeDetail != null && includeDetail;
            return commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationRepository.getNotificationHistory(orgId, id, doDetail, query));
        }
    }

    @Path(Constants.API_INTEGRATIONS_V_2_0 + "/endpoints")
    static class V2 extends EndpointResource {

        @GET
        @Path("/{id}/history")
        @Produces(APPLICATION_JSON)
        @Parameters({
            @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page, if not specified or 0 is used, returns a maximum of " + MAX_NOTIFICATION_HISTORY_RESULTS + " elements.",
                schema = @Schema(type = SchemaType.INTEGER)
                ),
            @Parameter(
                name = "pageNumber",
                in = ParameterIn.QUERY,
                description = "Page number. Starts at first page (0), if not specified starts at first page.",
                schema = @Schema(type = SchemaType.INTEGER)
                ),
            @Parameter(
                name = "includeDetail",
                description = "Include the detail in the reply",
                schema = @Schema(type = SchemaType.BOOLEAN)
                )
        })
        public Page<NotificationHistoryDTO> getEndpointHistory(
                @Context SecurityContext sec,
                @Context UriInfo uriInfo,
                @PathParam("id") UUID id,
                @QueryParam("includeDetail") Boolean includeDetail,
                @BeanParam Query query
        ) {
            if (this.backendConfig.isKesselRelationsEnabled()) {
                this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.VIEW_HISTORY, id);

                return this.internalGetEndpointHistory(sec, uriInfo, id, includeDetail, query);
            } else {
                return this.legacyRBACGetEndpointHistory(sec, uriInfo, id, includeDetail, query);
            }
        }

        @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
        protected Page<NotificationHistoryDTO> legacyRBACGetEndpointHistory(final SecurityContext securityContext, final UriInfo uriInfo, final UUID id, final Boolean includeDetail, final Query query) {
            return this.internalGetEndpointHistory(securityContext, uriInfo, id, includeDetail, query);
        }

        protected Page<NotificationHistoryDTO> internalGetEndpointHistory(final SecurityContext securityContext, final UriInfo uriInfo, final UUID id, final Boolean includeDetail, @Valid final Query query) {
            String orgId = getOrgId(securityContext);
            boolean doDetail = includeDetail != null && includeDetail;

            final List<NotificationHistory> notificationHistory = this.notificationRepository.getNotificationHistory(orgId, id, doDetail, query);
            final long notificationHistoryCount = this.notificationRepository.countNotificationHistoryElements(id, orgId);

            return new Page<>(
                commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationHistory),
                PageLinksBuilder.build(uriInfo.getPath(), notificationHistoryCount, query.getLimit().getLimit(), query.getLimit().getOffset()),
                new Meta(notificationHistoryCount)
            );
        }

        @GET
        @Path("/{id}")
        @Produces(APPLICATION_JSON)
        @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
        public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
            if (this.backendConfig.isKesselRelationsEnabled()) {
                this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.VIEW, id);

                return this.internalGetEndpoint(sec, id, false);
            } else {
                return legacyGetEndpoint(sec, id, true);
            }
        }

        @GET
        @Produces(APPLICATION_JSON)
        @Operation(summary = "List endpoints", description = "Provides a list of endpoints. Use this endpoint to find specific endpoints.")
        @Parameters({
            @Parameter(
                    name = "limit",
                    in = ParameterIn.QUERY,
                    description = "Number of items per page. If the value is 0, it will return all elements",
                    schema = @Schema(type = SchemaType.INTEGER)
                ),
            @Parameter(
                    name = "pageNumber",
                    in = ParameterIn.QUERY,
                    description = "Page number. Starts at first page (0), if not specified starts at first page.",
                    schema = @Schema(type = SchemaType.INTEGER)
                )
            })
        public EndpointPage getEndpoints(
            @Context                SecurityContext sec,
            @BeanParam @Valid       Query query,
            @QueryParam("type")     List<String> targetType,
            @QueryParam("active")   Boolean activeOnly,
            @QueryParam("name")     String name
        ) {
            if (this.backendConfig.isKesselRelationsEnabled()) {
                // Fetch the set of integration IDs the user is authorized to view.
                final Set<UUID> authorizedIds = this.kesselAuthorization.lookupAuthorizedIntegrations(sec, IntegrationPermission.VIEW);
                if (authorizedIds.isEmpty()) {
                    Log.infof("[org_id: %s][username: %s] Kessel did not return any integration IDs for the request", getOrgId(sec), getUsername(sec));

                    return new EndpointPage(new ArrayList<>(), new HashMap<>(), new Meta(0L));
                }

                return internalGetEndpoints(sec, query, targetType, activeOnly, name, authorizedIds, true);
            }

            return getEndpointsLegacyRBACRoles(sec, query, targetType, activeOnly, name, true);
        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List endpoints", description = "Provides a list of endpoints. Use this endpoint to find specific endpoints.")
    @Parameters({
        @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page. If the value is 0, it will return all elements",
                schema = @Schema(type = SchemaType.INTEGER)
            ),
        @Parameter(
                name = "pageNumber",
                in = ParameterIn.QUERY,
                description = "Page number. Starts at first page (0), if not specified starts at first page.",
                schema = @Schema(type = SchemaType.INTEGER)
            )
    })
    public EndpointPage getEndpoints(
        @Context                SecurityContext sec,
        @BeanParam @Valid       Query query,
        @QueryParam("type")     List<String> targetType,
        @QueryParam("active")   Boolean activeOnly,
        @QueryParam("name")     String name
    ) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            // Fetch the set of integration IDs the user is authorized to view.
            final Set<UUID> authorizedIds = this.kesselAuthorization.lookupAuthorizedIntegrations(sec, IntegrationPermission.VIEW);
            if (authorizedIds.isEmpty()) {
                Log.infof("[org_id: %s][username: %s] Kessel did not return any integration IDs for the request", getOrgId(sec), getUsername(sec));

                return new EndpointPage(new ArrayList<>(), new HashMap<>(), new Meta(0L));
            }

            return this.internalGetEndpoints(sec, query, targetType, activeOnly, name, authorizedIds, false);
        }

        return this.getEndpointsLegacyRBACRoles(sec, query, targetType, activeOnly, name, false);
    }

    /**
     * Gets the list of endpoints. Checks the principal's authorization by
     * looking at its roles.
     * @param securityContext the security context of the request.
     * @param query the page related query elements.
     * @param targetType the types of the endpoints to fetch.
     * @param activeOnly should only the active endpoints be fetched?
     * @param name filter endpoints by name.
     * @return a page containing the requested endpoints.
     */
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    protected EndpointPage getEndpointsLegacyRBACRoles(final SecurityContext securityContext, final Query query, final List<String> targetType, final Boolean activeOnly, final String name, final boolean includeLinkedEventTypes) {
        return this.internalGetEndpoints(securityContext, query, targetType, activeOnly, name, null, includeLinkedEventTypes);
    }

    /**
     * Gets the list of endpoints.
     * @param sec the security context of the request.
     * @param query the page related query elements.
     * @param targetType the types of the endpoints to fetch.
     * @param activeOnly should only the active endpoints be fetched?
     * @param name filter endpoints by name.
     * @param authorizedIds set of authorized integrations that we are allowed
     *                      to fetch.
     * @return a page containing the requested endpoints.
     */
    protected EndpointPage internalGetEndpoints(
        final SecurityContext sec,
        final Query query,
        final List<String> targetType,
        final Boolean activeOnly,
        final String name,
        final Set<UUID> authorizedIds,
        final boolean includeLinkedEventTypes
    ) {
        String orgId = getOrgId(sec);

        List<Endpoint> endpoints;
        Long count;

        Set<CompositeEndpointType> compositeType;

        if (targetType != null && targetType.size() > 0) {
            compositeType = targetType.stream().map(s -> {
                try {
                    return CompositeEndpointType.fromString(s);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown endpoint type: [" + s + "]", e);
                }
            }).collect(Collectors.toSet());
        } else {
            compositeType = Set.of();
        }

        endpoints = endpointRepository.getEndpointsPerCompositeType(orgId, name, compositeType, activeOnly, query, authorizedIds);
        count = endpointRepository.getEndpointsCountPerCompositeType(orgId, name, compositeType, activeOnly, authorizedIds);

        final List<EndpointDTO> endpointDTOS = new ArrayList<>(endpoints.size());
        for (Endpoint endpoint: endpoints) {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint);

            // Redact the secrets for the endpoint if the user does not have
            // permission.
            this.redactSecretsForEndpoint(sec, endpoint);

            EndpointDTO endpointDTO = endpointMapper.toDTO(endpoint);
            if (includeLinkedEventTypes) {
                includeLinkedEventTypes(endpoint.getEventTypes(), endpointDTO);
            }
            endpointDTOS.add(endpointDTO);
        }

        return new EndpointPage(endpointDTOS, new HashMap<>(), new Meta(count));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a new endpoint", description = "Creates a new endpoint by providing data such as a description, a name, and the endpoint properties. Use this endpoint to create endpoints for integration with third-party services such as webhooks, Slack, or Google Chat.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = EndpointDTO.class))),
        @APIResponse(responseCode = "400", description = "Bad data passed, that does not correspond to the definition or Endpoint.properties are empty")
    })
    public EndpointDTO createEndpoint(
        @Context                        SecurityContext sec,
        @RequestBody(required = true)   EndpointDTO endpointDTO
    ) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(sec, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            return this.internalCreateEndpoint(sec, endpointDTO);
        } else {
            return this.legacyRBACCreateEndpoint(sec, endpointDTO);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected EndpointDTO legacyRBACCreateEndpoint(final SecurityContext securityContext, final EndpointDTO endpointDTO) {
        return this.internalCreateEndpoint(securityContext, endpointDTO);
    }

    protected EndpointDTO internalCreateEndpoint(final SecurityContext securityContext, @NotNull @Valid final EndpointDTO endpointDTO) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        try {
            return this.endpointMapper.toDTO(
                this.internalCreateEndpoint(securityContext, endpoint, endpointDTO.eventTypes)
            );
        } catch (final Exception e) {
            // Clean up the secrets from Sources if any were created.
            this.secretUtils.deleteSecretsForEndpoint(endpoint);

            throw e;
        }
    }

    /**
     * Internal function which creates the given endpoint. The reason why there
     * is this internal function is so that we can wrap it with a "try/catch"
     * block, so that if any Sources secrets are created and an exception is
     * raised upon saving the endpoint, we can call Sources again to clean up
     * the secrets, as otherwise we would be leaving dangling secrets in
     * Sources.
     *
     * @param sec        the security context of the request.
     * @param endpoint   the endpoint to be created.
     * @param eventTypes
     * @return the created endpoint in the database.
     */
    @Transactional
    protected Endpoint internalCreateEndpoint(
            @Context                                        SecurityContext sec,
            @RequestBody(required = true) @NotNull @Valid   Endpoint endpoint,
            Set<UUID> eventTypes)  {
        if (!isEndpointTypeAllowed(endpoint.getType())) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpoint.getType());
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);

        if (endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        }

        if (endpoint.getType() == CAMEL) {
            String subType = endpoint.getSubType();

            if (subType.equals("slack")) {
                checkSlackChannel(endpoint.getProperties(CamelProperties.class), null);
            } else if (subType.equals("servicenow") || subType.equals("splunk")) {
                checkHttpsEndpoint(endpoint.getProperties(CamelProperties.class));
            }
        }

        endpoint.setStatus(EndpointStatus.READY);

        endpoint.setEventTypes(fetchEventTypes(eventTypes));

        this.secretUtils.createSecretsForEndpoint(endpoint);

        final Endpoint createdEndpoint = this.endpointRepository.createEndpoint(endpoint);

        if (this.backendConfig.isKesselInventoryEnabled()) {
            // Attempt creating the integration in Kessel's inventory. Any
            // exception here would roll back the operation and our integration
            // would not be created in our database either.
            this.kesselAssets.createIntegration(sec, WORKSPACE_ID_PLACEHOLDER, createdEndpoint.getId().toString());
        }

        return createdEndpoint;
    }

    private Set<EventType> fetchEventTypes(Set<UUID> eventTypesIds) {
        if (null != eventTypesIds && !eventTypesIds.isEmpty()) {
            List<EventType> eventTypes = eventTypeRepository.findByIds(eventTypesIds);
            if (eventTypes.size() != eventTypesIds.size()) {
                eventTypesIds.removeAll(eventTypes.stream().map(EventType::getId).toList());
                throw new NotFoundException(String.format("Event type '%s' not found", eventTypesIds.stream().findFirst().get()));
            }
            return eventTypes.stream().collect(Collectors.toSet());
        }
        return null;
    }

    private void checkSlackChannel(CamelProperties camelProperties, CamelProperties previousCamelProperties) {
        String channel = camelProperties.getExtras() != null ? camelProperties.getExtras().get("channel") : null;

        // throw an exception if we receive a channel on endpoint creation
        if (null == previousCamelProperties && channel != null) {
            throw new BadRequestException(DEPRECATED_SLACK_CHANNEL_ERROR);
        // throw an exception if we receive a channel update
        } else if (channel != null && !channel.equals(previousCamelProperties.getExtras().get("channel"))) {
            throw new BadRequestException(DEPRECATED_SLACK_CHANNEL_ERROR);
        }
    }

    private void checkHttpsEndpoint(CamelProperties camelProperties) {
        if (camelProperties != null) {
            URI endpointUri = URI.create(camelProperties.getUrl());

            if (!endpointUri.getScheme().equalsIgnoreCase("https")) {
                // throw an exception if a non-HTTPS URL scheme is used on endpoint creation or update
                throw new BadRequestException(HTTPS_ENDPOINT_SCHEME_REQUIRED);
            }
        }
    }

    @POST
    @Path("/system/email_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create an email subscription endpoint", description = "Adds the email subscription endpoint into the system and specifies the role-based access control (RBAC) group that will receive email notifications. Use this endpoint in behavior groups to send emails when an action linked to the behavior group is triggered.")
    @Transactional
    public EndpointDTO getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec, @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        final Endpoint endpoint;

        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(sec, WorkspacePermission.CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            endpoint = this.getOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION);
        } else {
            endpoint = this.legacyRBACGetOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION);
        }

        return this.endpointMapper.toDTO(endpoint);
    }

    @POST
    @Path("/system/drawer_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a drawer endpoint", description = "Adds the drawer system endpoint into the system and specifies the role-based access control (RBAC) group that will receive notifications. Use this endpoint to add an animation as a notification in the UI.")
    @Transactional
    public EndpointDTO getOrCreateDrawerSubscriptionEndpoint(@Context SecurityContext sec, @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        final Endpoint endpoint;

        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.hasPermissionOnResource(sec, WorkspacePermission.CREATE_DRAWER_INTEGRATION, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

            endpoint = this.getOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER);
        } else {
            endpoint = this.legacyRBACGetOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER);
        }

        return this.endpointMapper.toDTO(endpoint);
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    protected Endpoint legacyRBACGetOrCreateSystemSubscriptionEndpoint(final SecurityContext securityContext, final RequestSystemSubscriptionProperties requestProps, final EndpointType endpointType) {
        return this.getOrCreateSystemSubscriptionEndpoint(securityContext, requestProps, endpointType);
    }

    protected Endpoint getOrCreateSystemSubscriptionEndpoint(SecurityContext sec, @NotNull @Valid RequestSystemSubscriptionProperties requestProps, EndpointType endpointType) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        getOrCreateInternalEndpointCommonChecks(requestProps, principal);

        // Prevent from creating not public facing properties
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());
        properties.setGroupId(requestProps.getGroupId());

        return endpointRepository.getOrCreateSystemSubscriptionEndpoint(accountId, orgId, properties, endpointType);
    }

    private void getOrCreateInternalEndpointCommonChecks(RequestSystemSubscriptionProperties requestProps, RhIdPrincipal principal) {
        if (requestProps.getGroupId() != null && requestProps.isOnlyAdmins()) {
            throw new BadRequestException("Cannot use RBAC groups and only admins in the same endpoint");
        }

        if (requestProps.getGroupId() != null) {
            boolean isValid = rbacGroupValidator.validate(requestProps.getGroupId(), principal.getIdentity().rawIdentity);
            if (!isValid) {
                throw new BadRequestException(String.format("Invalid RBAC group identified with id %s", requestProps.getGroupId()));
            }
        }
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.VIEW, id);

            return this.internalGetEndpoint(sec, id, false);
        } else {
            return this.legacyGetEndpoint(sec, id, false);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    protected EndpointDTO legacyGetEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes) {
        return this.internalGetEndpoint(securityContext, id, includeLinkedEventTypes);
    }

    protected EndpointDTO internalGetEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes) {
        String orgId = getOrgId(securityContext);
        Optional<Endpoint> endpoint = endpointRepository.getEndpointWithLinkedEventTypes(orgId, id);
        if (endpoint.isEmpty()) {
            throw new NotFoundException();
        } else {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint.get());

            // Redact all the credentials from the endpoint's properties.
            this.redactSecretsForEndpoint(securityContext, endpoint.get());

            EndpointDTO endpointDTO = this.endpointMapper.toDTO(endpoint.get());
            if (includeLinkedEventTypes) {
                includeLinkedEventTypes(endpoint.get().getEventTypes(), endpointDTO);
            }
            return endpointDTO;
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete an endpoint", description = "Deletes an endpoint. Use this endpoint to delete an endpoint that is no longer needed. Deleting an endpoint that is already linked to a behavior group will unlink it from the behavior group. You cannot delete system endpoints.")
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.DELETE, id);

            final Response noContentResponse = this.internalDeleteEndpoint(sec, id);

            if (this.backendConfig.isKesselInventoryEnabled()) {
                // Attempt deleting the integration from Kessel. If any exception
                // is thrown the whole transaction will be rolled back and the
                // integration will not be deleted from our database.
                this.kesselAssets.deleteIntegration(sec, WORKSPACE_ID_PLACEHOLDER, id.toString());
            }

            return noContentResponse;
        } else {
            return this.legacyRBACDeleteEndpoint(sec, id);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected Response legacyRBACDeleteEndpoint(final SecurityContext securityContext, final UUID id) {
        return this.internalDeleteEndpoint(securityContext, id);
    }

    private Response internalDeleteEndpoint(final SecurityContext securityContext, final UUID id) {
        String orgId = getOrgId(securityContext);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpointType);

        // Clean up the secrets in Sources.
        final Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        endpointRepository.deleteEndpoint(orgId, id);

        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Enable an endpoint", description = "Enables an endpoint that is disabled so that the endpoint will be executed on the following operations that use the endpoint. An operation must be restarted to use the enabled endpoint.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.ENABLE, id);

            return this.internalEnableEndpoint(sec, id);
        } else {
            return this.legacyRBACEnableEndpoint(sec, id);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected Response legacyRBACEnableEndpoint(final SecurityContext securityContext, final UUID id) {
        return this.internalEnableEndpoint(securityContext, id);
    }

    private Response internalEnableEndpoint(final SecurityContext securityContext, final UUID id) {
        String orgId = getOrgId(securityContext);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpointType);
        endpointRepository.enableEndpoint(orgId, id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/enable")
    @Operation(summary = "Disable an endpoint", description = "Disables an endpoint so that the endpoint will not be executed after an operation that uses the endpoint is started. An operation that is already running can still execute the endpoint. Disable an endpoint when you want to stop it from running and might want to re-enable it in the future.")
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.DISABLE, id);

            return this.internalDisableEndpoint(sec, id);
        } else {
            return this.legacyRBACDisableEndpoint(sec, id);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected Response legacyRBACDisableEndpoint(final SecurityContext securityContext, final UUID id) {
        return this.internalDisableEndpoint(securityContext, id);
    }

    private Response internalDisableEndpoint(final SecurityContext securityContext, final UUID id) {
        String orgId = getOrgId(securityContext);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpointType);
        endpointRepository.disableEndpoint(orgId, id);
        return Response.noContent().build();
    }

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Update an endpoint", description = "Updates the endpoint configuration. Use this to update an existing endpoint. Any changes to the endpoint take place immediately.")
    @Path("/{id}")
    @Produces(TEXT_PLAIN)
    @PUT
    public Response updateEndpoint(
        @Context                                        SecurityContext securityContext,
        @PathParam("id")                                UUID id,
        @RequestBody(required = true) @NotNull @Valid   EndpointDTO endpointDTO
    ) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(securityContext, IntegrationPermission.EDIT, id);

            return this.internalUpdateEndpoint(securityContext, id, endpointDTO);
        }

        return this.updateEndpointLegacyRBACRoles(securityContext, id, endpointDTO);
    }

    /**
     * Updates an endpoint. Checks the principal's authorization by looking at
     * its roles.
     * @param securityContext the security context of the request.
     * @param endpointId the ID of the endpoint to be updated.
     * @param endpointDTO the received request body.
     * @return a response specifying the outcome of the operation.
     */
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected Response updateEndpointLegacyRBACRoles(final SecurityContext securityContext, final UUID endpointId, final EndpointDTO endpointDTO) {
        return this.internalUpdateEndpoint(securityContext, endpointId, endpointDTO);
    }

    /**
     * Updates an endpoint.
     * @param sec the security context of the request.
     * @param id the endpoint's identifier.
     * @param endpointDTO the updated endpoint's body.
     * @return a response specifying the outcome of the oeration.
     */
    @Transactional
    protected Response internalUpdateEndpoint(final SecurityContext sec, final UUID id, final @NotNull @Valid EndpointDTO endpointDTO) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        if (!isEndpointTypeAllowed(endpoint.getType())) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        // This prevents from updating an endpoint from whatever EndpointType to a system EndpointType
        checkSystemEndpoint(endpoint.getType());
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setId(id);

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, id);
        EndpointType endpointType = dbEndpoint.getType();

        // This prevents from updating an endpoint from system EndpointType to a whatever EndpointType
        checkSystemEndpoint(endpointType);

        if (endpoint.getType() == CAMEL) {
            String subType = endpoint.getSubType();

            if (subType.equals("slack")) {
                checkSlackChannel(endpoint.getProperties(CamelProperties.class), dbEndpoint.getProperties(CamelProperties.class));
            } else if (subType.equals("servicenow") || subType.equals("splunk")) {
                checkHttpsEndpoint(endpoint.getProperties(CamelProperties.class));
            }
        }

        endpointRepository.updateEndpoint(endpoint);

        // Update the secrets in Sources.
        final Endpoint updatedDbEndpoint = endpointRepository.getEndpoint(orgId, id);
        final EndpointProperties endpointProperties = endpoint.getProperties();
        final EndpointProperties databaseEndpointProperties = updatedDbEndpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable incomingProperties && databaseEndpointProperties instanceof SourcesSecretable dep) {
            // In order to be able to update the secrets in Sources, we need to grab the IDs of these secrets from the
            // database endpoint, since the client won't be sending those IDs.
            dep.setBasicAuthentication(incomingProperties.getBasicAuthentication());
            dep.setSecretToken(incomingProperties.getSecretToken());
            dep.setBearerAuthentication(incomingProperties.getBearerAuthentication());
            this.secretUtils.updateSecretsForEndpoint(updatedDbEndpoint);
        }

        return Response.ok().build();
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve event notification details", description = "Retrieves extended information about the outcome of an event notification related to the specified endpoint. Use this endpoint to learn why an event delivery failed.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.VIEW_HISTORY, endpointId);

            return this.internalGetDetailedEndpointHistory(sec, endpointId, historyId);
        } else {
            return this.legacyRBACGetDetailedEndpointHistory(sec, endpointId, historyId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    protected Response legacyRBACGetDetailedEndpointHistory(final SecurityContext securityContext, final UUID endpointId, final UUID historyId) {
        return this.internalGetDetailedEndpointHistory(securityContext, endpointId, historyId);
    }

    private Response internalGetDetailedEndpointHistory(final SecurityContext securityContext, final UUID endpointId, final UUID historyId) {
        String orgId = getOrgId(securityContext);
        JsonObject json = notificationRepository.getNotificationDetails(orgId, endpointId, historyId);
        if (json == null) {
            // Maybe 404 should only be returned if history_id matches nothing? Otherwise 204
            throw new NotFoundException();
        } else {
            if (json.isEmpty()) {
                return Response.noContent().build();
            }
            return Response.ok(json).build();
        }
    }

    /**
     * Sends an integration test event via the specified endpoint.
     * @param uuid the {@link UUID} of the endpoint to test.
     * @return a "no content" response on success.
     */
    @APIResponse(responseCode = "204", description = "No Content")
    @Consumes(APPLICATION_JSON)
    @POST
    @Path("/{uuid}/test")
    @Operation(summary = "Generate a test notification", description = "Generates a test notification for a particular endpoint. Use this endpoint to test that an integration that you created works as expected. This endpoint triggers a test notification that should be received by the target recipient. For example, if you set up a webhook as the action to take upon receiving a notification, you should receive a test notification when using this endpoint.")
    @Parameters({
        @Parameter(
                name = "uuid",
                in = ParameterIn.PATH,
                description = "The UUID of the endpoint to test",
                schema = @Schema(type = SchemaType.STRING)
            )
    })
    public void testEndpoint(@Context SecurityContext sec, @RestPath UUID uuid, @RequestBody final EndpointTestRequest requestBody) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(sec, IntegrationPermission.TEST, uuid);

            this.internalTestEndpoint(sec, uuid, requestBody);
        } else {
            if (!this.endpointRepository.existsByUuidAndOrgId(uuid, getOrgId(sec))) {
                throw new NotFoundException("integration not found");
            }

            this.legacyRBACTestEndpoint(sec, uuid, requestBody);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    protected void legacyRBACTestEndpoint(final SecurityContext securityContext, final UUID uuid,  final EndpointTestRequest requestBody) {
        this.internalTestEndpoint(securityContext, uuid, requestBody);
    }

    protected void internalTestEndpoint(final SecurityContext securityContext, final UUID uuid, @Valid final EndpointTestRequest requestBody) {
        final InternalEndpointTestRequest internalEndpointTestRequest = new InternalEndpointTestRequest();
        internalEndpointTestRequest.endpointUuid = uuid;
        internalEndpointTestRequest.orgId = getOrgId(securityContext);
        if (requestBody != null) {
            internalEndpointTestRequest.message = requestBody.message;
        }

        this.endpointTestService.testEndpoint(internalEndpointTestRequest);
    }

    private static void checkSystemEndpoint(EndpointType endpointType) {
        if (endpointType.isSystemEndpointType) {
            throw new BadRequestException(String.format(
                    "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                    endpointType
            ));
        }
    }

    private boolean isEndpointTypeAllowed(EndpointType endpointType) {
        return !backendConfig.isEmailsOnlyModeEnabled() || endpointType.isSystemEndpointType;
    }

    /**
     * Removes the secrets from the endpoint's properties when returning them
     * to the client.
     * @param endpoint the endpoint to redact the secrets from.
     */
    @Deprecated(forRemoval = true)
    protected void redactSecretsForEndpoint(final SecurityContext securityContext, final Endpoint endpoint) {
        // Figure out if the principal has "write" permissions on the
        // integration or not, to decide whether we should redact the secrets
        // from the returning payload.
        //
        // Users with just read permissions will get the secrets redacted for
        // them.
        boolean shouldRedactSecrets;
        if (this.backendConfig.isKesselRelationsEnabled()) {
            try {
                this.kesselAuthorization.hasPermissionOnResource(securityContext, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpoint.getId().toString());
                shouldRedactSecrets = false;
            } catch (final ForbiddenException e) {
                shouldRedactSecrets = true;
            }
        } else {
            shouldRedactSecrets = !securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS);
        }

        if (shouldRedactSecrets) {
            if (endpoint.getProperties() instanceof SourcesSecretable sourcesSecretable) {
                final BasicAuthentication basicAuthentication = sourcesSecretable.getBasicAuthentication();
                if (basicAuthentication != null) {
                    basicAuthentication.setPassword(REDACTED_CREDENTIAL);
                    basicAuthentication.setUsername(REDACTED_CREDENTIAL);
                }

                final String bearerToken = sourcesSecretable.getBearerAuthentication();
                if (bearerToken != null) {
                    sourcesSecretable.setBearerAuthentication(REDACTED_CREDENTIAL);
                }

                final String secretToken = sourcesSecretable.getSecretToken();
                if (secretToken != null) {
                    sourcesSecretable.setSecretToken(REDACTED_CREDENTIAL);
                }
            }
        }
    }

    @DELETE
    @Path("/{endpointId}/eventType/{eventTypeId}")
    @Operation(summary = "Delete the link between an endpoint and an event type", description = "Delete the link between an endpoint and an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with the passed id.")
    })
    @Transactional
    public void deleteEventTypeFromEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @RestPath final UUID endpointId) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(securityContext, IntegrationPermission.EDIT, endpointId);

            internalDeleteEventTypeFromEndpoint(securityContext, eventTypeId, endpointId);
        } else {
            legacyRBACDeleteEventTypeFromEndpoint(securityContext, eventTypeId, endpointId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    protected void legacyRBACDeleteEventTypeFromEndpoint(final SecurityContext securityContext, final UUID eventTypeId, final UUID endpointId) {
        internalDeleteEventTypeFromEndpoint(securityContext, eventTypeId, endpointId);
    }

    private void internalDeleteEventTypeFromEndpoint(final SecurityContext securityContext, final UUID eventTypeId, final UUID endpointId) {
        final String orgId = getOrgId(securityContext);
        endpointEventTypeRepository.deleteEndpointFromEventType(eventTypeId, endpointId, orgId);

        // Sync behavior group model
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEndpointId(orgId, endpointId);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            if (behaviorGroup.getBehaviors().stream().anyMatch(bg -> bg.getId().eventTypeId.equals(eventTypeId))) {
                if (behaviorGroup.getActions().size() == 1) {
                    behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
                } else {
                    behaviorGroupRepository.deleteEndpointFromBehaviorGroup(behaviorGroup.getId(), endpointId, orgId);
                }
            }
        }
    }

    @PUT
    @Path("/{endpointId}/eventType/{eventTypeId}")
    @Operation(summary = "Add a link between an endpoint and an event type", description = "Add a link between an endpoint and an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with the passed id.")
    })
    @Transactional
    public void addEventTypeToEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @RestPath final UUID endpointId) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(securityContext, IntegrationPermission.EDIT, endpointId);

            internalAddEventTypeToEndpoint(securityContext, eventTypeId, endpointId);
        } else {
            legacyRbacAddEventTypeToEndpoint(securityContext, eventTypeId, endpointId);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public void legacyRbacAddEventTypeToEndpoint(final SecurityContext securityContext, final UUID eventTypeId, final UUID endpointId) {
        internalAddEventTypeToEndpoint(securityContext, eventTypeId, endpointId);
    }

    private void internalAddEventTypeToEndpoint(final SecurityContext securityContext, final UUID eventTypeId, final UUID endpointId) {
        final String orgId = getOrgId(securityContext);
        final String accountId = getAccountId(securityContext);

        Endpoint updatedEndpoint = endpointEventTypeRepository.addEventTypeToEndpoint(eventTypeId, endpointId, orgId);

        // Sync behavior group model
        createOrUpdateLinkedBehaviorGroup(Set.of(eventTypeId), endpointId, updatedEndpoint.getName(), orgId, accountId);
    }

    @PUT
    @Path("/{endpointId}/eventTypes")
    @Operation(summary = "Update  links between an endpoint and event types", description = "Update  links between an endpoint and event types.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with passed ids.")
    })
    @Transactional
    public void updateEventTypesLinkedToEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID endpointId, @Parameter(description = "Set of event type ids to associate") Set<UUID> eventTypeIds) {
        if (this.backendConfig.isKesselRelationsEnabled()) {
            this.kesselAuthorization.isPrincipalAuthorizedAndDoesIntegrationExist(securityContext, IntegrationPermission.EDIT, endpointId);

            internalUpdateEventTypesLinkedToEndpoint(securityContext, endpointId, eventTypeIds);
        } else {
            legacyRbacUpdateEventTypesLinkedToEndpoint(securityContext, endpointId, eventTypeIds);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public void legacyRbacUpdateEventTypesLinkedToEndpoint(final SecurityContext securityContext, final UUID endpointId, final Set<UUID> eventTypeIds) {
        internalUpdateEventTypesLinkedToEndpoint(securityContext, endpointId, eventTypeIds);
    }

    private void internalUpdateEventTypesLinkedToEndpoint(final SecurityContext securityContext, final UUID endpointId, final Set<UUID> eventTypeIds) {
        final String orgId = getOrgId(securityContext);
        final String accountId = getAccountId(securityContext);
        Endpoint updatedEndpoint =  endpointEventTypeRepository.updateEventTypesLinkedToEndpoint(endpointId, eventTypeIds, orgId);

        // Sync behavior group model

        // delete endpoint from existing behavior group
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEndpointId(orgId, endpointId);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            if (behaviorGroup.getActions().size() == 1) {
                behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
            } else {
                behaviorGroupRepository.deleteEndpointFromBehaviorGroup(behaviorGroup.getId(), endpointId, orgId);
            }
        }

        // Create or update relevant behavior groups
        createOrUpdateLinkedBehaviorGroup(eventTypeIds, endpointId, updatedEndpoint.getName(), orgId, accountId);
    }

    private void createOrUpdateLinkedBehaviorGroup(Set<UUID> eventTypeIds, UUID endpointId, String endpointName, String orgId, String accountId) {
        String behaviorGroupName = String.format("Integration \"%s\" behavior group", endpointName);

        // group event types by bundle
        Map<UUID, Set<UUID>> eventTypesGroupedByBundle = new HashMap<>();
        for (UUID eventTypeId : eventTypeIds) {
            Optional<Bundle> bundle = eventTypeRepository.findBundleByEventTypeId(eventTypeId);
            if (bundle.isPresent()) {
                eventTypesGroupedByBundle.computeIfAbsent(bundle.get().getId(), ignored -> new HashSet<>())
                        .add(eventTypeId);
            }
        }

        for (UUID bundleId : eventTypesGroupedByBundle.keySet()) {
            Optional<BehaviorGroup> existingBg = behaviorGroupRepository.findBehaviorGroupsByName(orgId, bundleId, behaviorGroupName);
            if (existingBg.isPresent()) {
                Boolean alreadyAssociatedAction = existingBg.get().getActions().stream().anyMatch(bga -> bga.getId().endpointId.equals(endpointId));

                if (!alreadyAssociatedAction) {
                    int position = existingBg.get().getActions().stream().mapToInt(ba -> ba.getPosition()).max().orElse(-1) + 1;
                    behaviorGroupRepository.appendActionToBehaviorGroup(existingBg.get().getId(), endpointId, position, orgId);
                }
                for (UUID eventTypeId : eventTypesGroupedByBundle.get(bundleId)) {
                    Boolean alreadyAssociatedEventType = existingBg.get().getBehaviors().stream().anyMatch(bh -> bh.getId().eventTypeId.equals(eventTypeId));
                    if (!alreadyAssociatedEventType) {
                        behaviorGroupRepository.appendBehaviorGroupToEventType(orgId, existingBg.get().getId(), eventTypeId);
                    }
                }
            } else {
                // Create or update legacy behavior group structure
                BehaviorGroup behaviorGroup = new BehaviorGroup();
                behaviorGroup.setBundleId(bundleId);
                behaviorGroup.setDisplayName(behaviorGroupName);

                behaviorGroupRepository.createFull(
                    accountId,
                    orgId,
                    behaviorGroup,
                    List.of(endpointId),
                    eventTypesGroupedByBundle.get(bundleId)
                );
            }
        }
    }

    private void includeLinkedEventTypes(Set<EventType> eventTypes, EndpointDTO endpointDTO) {
        if (null != eventTypes && !eventTypes.isEmpty()) {
            Map<Application, List<EventType>> applicationMap = eventTypes.stream()
                .sorted(Comparator.comparing(EventType::getDisplayName))
                .collect(Collectors.groupingBy(EventType::getApplication));
            Map<Bundle, List<Application>> bundleMap = applicationMap.keySet().stream()
                .sorted(Comparator.comparing(Application::getDisplayName))
                .collect(Collectors.groupingBy(Application::getBundle));

            List<Bundle> bundleList = bundleMap.keySet().stream().sorted(Comparator.comparing(Bundle::getDisplayName)).toList();

            Set<BundleDTO> bundleDTOSet = new LinkedHashSet<>();
            for (Bundle bundle : bundleList) {
                Set<ApplicationDTO> applicationDTOSet = new LinkedHashSet<>();
                List<Application> applications = bundleMap.get(bundle);
                for (Application application : applications) {
                    ApplicationDTO applicationDTO = commonMapper.applicationToApplicationDTO(application);
                    Set<EventTypeDTO> eventTypesDTO = new LinkedHashSet<>();
                    eventTypesDTO.addAll(commonMapper.eventTypeListToEventTypeDTOList(applicationMap.get(application)));
                    applicationDTO.setEventTypes(eventTypesDTO);
                    applicationDTOSet.add(applicationDTO);
                }
                BundleDTO bundleDTO = commonMapper.bundleToBundleDTO(bundle);
                bundleDTO.setApplications(applicationDTOSet);
                bundleDTOSet.add(bundleDTO);
            }
            endpointDTO.setEventTypesGroupByBundlesAndApplications(bundleDTOSet);
        }
    }
}
