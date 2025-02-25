package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.auth.annotation.IntegrationId;
import com.redhat.cloud.notifications.auth.kessel.KesselAssets;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rbac.RbacGroupValidator;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
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
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.v1.ApplicationDTO;
import com.redhat.cloud.notifications.models.dto.v1.BundleDTO;
import com.redhat.cloud.notifications.models.dto.v1.CommonMapper;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonObject;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsResponse;

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
    public static final String AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE = "Integration \"%s\" behavior group";

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

    @Inject
    WorkspaceUtils workspaceUtils;

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
        @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.VIEW_HISTORY})
        public List<NotificationHistoryDTO> getEndpointHistory(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @Valid @BeanParam Query query) {
            if (!this.endpointRepository.existsByUuidAndOrgId(id, getOrgId(sec))) {
                throw new NotFoundException("Endpoint not found");
            }

            // TODO We need globally limitations (Paging support and limits etc)
            String orgId = getOrgId(sec);
            boolean doDetail = includeDetail != null && includeDetail;
            return commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationRepository.getNotificationHistory(orgId, id, doDetail, query));
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
        Set<UUID> authorizedIds = null;
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            // Fetch the set of integration IDs the user is authorized to view.

            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

            // add permission as argument -- rather than assuming it underneath
            final Multi<ListNotificationsIntegrationsResponse> responseMulti = this.kesselAssets.listIntegrations(sec, workspaceId.toString());
            authorizedIds = responseMulti.map(ListNotificationsIntegrationsResponse::getIntegrations)
                    .map(i -> i.getReporterData().getLocalResourceId())
                    .map(UUID::fromString)
                    .collect()
                    .asSet()
                    .await().indefinitely();

            if (authorizedIds.isEmpty()) {
                Log.infof("[org_id: %s][username: %s] Kessel did not return any integration IDs for the request", getOrgId(sec), getUsername(sec));

                return new EndpointPage(new ArrayList<>(), new HashMap<>(), new Meta(0L));
            }
        } else {
            // Legacy RBAC permission checking. The permission will have been
            // prefetched and processed by the "ConsoleIdentityProvider".
            if (!sec.isUserInRole(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)) {
                throw new ForbiddenException();
            }
        }

        return internalGetEndpoints(sec, query, targetType, activeOnly, name, authorizedIds, false);
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = {WorkspacePermission.INTEGRATIONS_CREATE})
    public EndpointDTO createEndpoint(
        @Context                        final SecurityContext sec,
        @NotNull @Valid @RequestBody    final EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        try {
            return this.endpointMapper.toDTO(
                this.internalCreateEndpoint(sec, endpoint, endpointDTO.eventTypes)
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
        final SecurityContext sec,
        final  Endpoint endpoint,
        final Set<UUID> eventTypes
    ) {
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

        endpoint.setEventTypes(endpointEventTypeRepository.fetchAndValidateEndpointsEventTypesAssociation(eventTypes, Set.of(endpoint.getType())));

        //this.secretUtils.createSecretsForEndpoint(endpoint);

        final Endpoint createdEndpoint = this.endpointRepository.createEndpoint(endpoint);

        if (this.backendConfig.isKesselInventoryEnabled(orgId)) {
            // Attempt creating the integration in Kessel's inventory. Any
            // exception here would roll back the operation and our integration
            // would not be created in our database either.
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

            this.kesselAssets.createIntegration(sec, workspaceId.toString(), createdEndpoint.getId().toString());
        }

        // Sync behavior group model
        if (null != eventTypes && !eventTypes.isEmpty()) {
            createOrUpdateLinkedBehaviorGroup(eventTypes, createdEndpoint.getId(), createdEndpoint.getName(), orgId, accountId);
        }

        return createdEndpoint;
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = {WorkspacePermission.CREATE_EMAIL_SUBSCRIPTION_INTEGRATION})
    @Transactional
    public EndpointDTO getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(getOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION));
    }

    @POST
    @Path("/system/drawer_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a drawer endpoint", description = "Adds the drawer system endpoint into the system and specifies the role-based access control (RBAC) group that will receive notifications. Use this endpoint to add an animation as a notification in the UI.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = {WorkspacePermission.CREATE_DRAWER_INTEGRATION})
    @Transactional
    public EndpointDTO getOrCreateDrawerSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(this.getOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER));
    }

    protected Endpoint getOrCreateSystemSubscriptionEndpoint(SecurityContext sec, RequestSystemSubscriptionProperties requestProps, EndpointType endpointType) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        getOrCreateInternalEndpointCommonChecks(requestProps, principal);

        // Prevent from creating not public facing properties
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());
        properties.setGroupId(requestProps.getGroupId());

        Optional<Endpoint> getEndpoint = endpointRepository.getSystemSubscriptionEndpoint(orgId, properties, endpointType);
        if (getEndpoint.isPresent()) {
            return getEndpoint.get();
        } else {
            Endpoint createdEndpoint =  endpointRepository.createSystemSubscriptionEndpoint(accountId, orgId, properties, endpointType);
            if (this.backendConfig.isKesselInventoryEnabled(orgId)) {
                // Attempt creating the integration in Kessel's inventory. Any
                // exception here would roll back the operation and our integration
                // would not be created in our database either.
                final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

                this.kesselAssets.createIntegration(sec, workspaceId.toString(), createdEndpoint.getId().toString());
            }
            return createdEndpoint;
        }
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.VIEW})
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id) {
        return internalGetEndpoint(sec, id, false);
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.DELETE})
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpointType);

        // Clean up the secrets in Sources.
        final Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);

        endpointRepository.deleteEndpoint(orgId, id);

        if (this.backendConfig.isKesselInventoryEnabled(orgId)) {
            // Attempt deleting the integration from Kessel. If any exception
            // is thrown the whole transaction will be rolled back and the
            // integration will not be deleted from our database.
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(orgId);

            this.kesselAssets.deleteIntegration(sec, workspaceId.toString(), id.toString());
        }

        // Attempt deleting the secrets for the given endpoint. In the case
        // that the secrets deletion goes wrong:
        //
        // - The transaction will be rolled back and the integration will not
        // be deleted.
        // - The secrets will not have been deleted from Sources.
        // - We need to recreate the integration in Kessel Inventory, so that
        // everything stays in sync.
        try {
            //this.secretUtils.deleteSecretsForEndpoint(endpoint);
        } catch (final Exception e) {
            if (this.backendConfig.isIgnoreSourcesErrorOnEndpointDelete(orgId)) {
                Log.errorf(e, "Sources error deleting endpoint %s", endpoint);
            } else {
                if (this.backendConfig.isKesselInventoryEnabled(orgId)) {
                    final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(orgId);

                    this.kesselAssets.createIntegration(sec, workspaceId.toString(), id.toString());
                }

                throw e;
            }
        }

        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Enable an endpoint", description = "Enables an endpoint that is disabled so that the endpoint will be executed on the following operations that use the endpoint. An operation must be restarted to use the enabled endpoint.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.ENABLE})
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.DISABLE})
    public Response disableEndpoint(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
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
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.EDIT})
    public Response updateEndpoint(
        @Context                                        SecurityContext securityContext,
        @PathParam("id")              @IntegrationId    UUID id,
        @RequestBody(required = true) @NotNull @Valid   EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        if (!isEndpointTypeAllowed(endpoint.getType())) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        // This prevents from updating an endpoint from whatever EndpointType to a system EndpointType
        checkSystemEndpoint(endpoint.getType());
        String accountId = getAccountId(securityContext);
        String orgId = getOrgId(securityContext);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setId(id);

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, id);
        if (dbEndpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }
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

        if (!dbEndpoint.getName().equals(endpoint.getName())) {
            String behaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, dbEndpoint.getName());
            String newBehaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpoint.getName());
            behaviorGroupRepository.updateBehaviorGroupName(dbEndpoint.getOrgId(), behaviorGroupName, newBehaviorGroupName);
        }

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

        if (null != endpointDTO.eventTypes) {
            internalUpdateEventTypesLinkedToEndpoint(securityContext, id, endpointDTO.eventTypes);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve event notification details", description = "Retrieves extended information about the outcome of an event notification related to the specified endpoint. Use this endpoint to learn why an event delivery failed.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.VIEW_HISTORY})
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
        String orgId = getOrgId(sec);
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.TEST})
    public void testEndpoint(@Context SecurityContext sec, @IntegrationId @RestPath UUID uuid, @Valid @RequestBody final EndpointTestRequest requestBody) {
        if (!this.endpointRepository.existsByUuidAndOrgId(uuid, getOrgId(sec))) {
            throw new NotFoundException("integration not found");
        }

        final InternalEndpointTestRequest internalEndpointTestRequest = new InternalEndpointTestRequest();
        internalEndpointTestRequest.endpointUuid = uuid;
        internalEndpointTestRequest.orgId = getOrgId(sec);
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
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(securityContext))) {
            try {
                this.kesselAuthorization.hasUpdatePermissionOnResource(securityContext, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpoint.getId().toString());
                shouldRedactSecrets = false;
            } catch (final ForbiddenException | NotFoundException e) {
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
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, integrationPermissions = {IntegrationPermission.EDIT})
    public void deleteEventTypeFromEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @IntegrationId @RestPath final UUID endpointId) {
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
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, integrationPermissions = {IntegrationPermission.EDIT})
    public void addEventTypeToEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @IntegrationId @RestPath final UUID endpointId) {
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, integrationPermissions = {IntegrationPermission.EDIT})
    public void updateEventTypesLinkedToEndpoint(@Context final SecurityContext securityContext, @IntegrationId @RestPath final UUID endpointId, @Parameter(description = "Set of event type ids to associate") Set<UUID> eventTypeIds) {
        internalUpdateEventTypesLinkedToEndpoint(securityContext, endpointId, eventTypeIds);
    }

    private void internalUpdateEventTypesLinkedToEndpoint(final SecurityContext securityContext, final UUID endpointId, final Set<UUID> eventTypeIds) {
        final String orgId = getOrgId(securityContext);
        final String accountId = getAccountId(securityContext);
        final Endpoint updatedEndpoint = endpointEventTypeRepository.updateEventTypesLinkedToEndpoint(endpointId, eventTypeIds, orgId);

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
        String behaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpointName);

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
