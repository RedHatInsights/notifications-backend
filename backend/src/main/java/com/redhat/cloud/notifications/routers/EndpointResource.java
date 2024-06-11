package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rbac.RbacGroupValidator;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.mappers.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
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
    @RestClient
    EndpointTestService endpointTestService;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    RbacGroupValidator rbacGroupValidator;

    @Inject
    BackendConfig backendConfig;

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
        @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
        public List<NotificationHistory> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @BeanParam @Valid Query query) {
            // TODO We need globally limitations (Paging support and limits etc)
            String orgId = getOrgId(sec);
            boolean doDetail = includeDetail != null && includeDetail;
            return notificationRepository.getNotificationHistory(orgId, id, doDetail, query);
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
        public Page<NotificationHistory> getEndpointHistory(
                @Context SecurityContext sec,
                @Context UriInfo uriInfo,
                @PathParam("id") UUID id,
                @QueryParam("includeDetail") Boolean includeDetail,
                @BeanParam Query query
        ) {
            String orgId = getOrgId(sec);
            boolean doDetail = includeDetail != null && includeDetail;

            final List<NotificationHistory> notificationHistory = this.notificationRepository.getNotificationHistory(orgId, id, doDetail, query);
            final long notificationHistoryCount = this.notificationRepository.countNotificationHistoryElements(id, orgId);

            return new Page<>(
                    notificationHistory,
                    PageLinksBuilder.build(uriInfo.getPath(), notificationHistoryCount, query.getLimit().getLimit(), query.getLimit().getOffset()),
                    new Meta(notificationHistoryCount)
            );
        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
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
            @Context SecurityContext sec,
            @BeanParam @Valid Query query,
            @QueryParam("type") List<String> targetType,
            @QueryParam("active") Boolean activeOnly,
            @QueryParam("name") String name) {
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

        endpoints = endpointRepository
                .getEndpointsPerCompositeType(orgId, name, compositeType, activeOnly, query);
        count = endpointRepository.getEndpointsCountPerCompositeType(orgId, name, compositeType, activeOnly);

        final List<EndpointDTO> endpointDTOS = new ArrayList<>(endpoints.size());
        for (Endpoint endpoint: endpoints) {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint);

            // Redact the secrets for the endpoint if the user does not have
            // permission.
            this.redactSecretsForEndpoint(sec, endpoint);

            endpointDTOS.add(
                this.endpointMapper.toDTO(endpoint)
            );
        }

        return new EndpointPage(endpointDTOS, new HashMap<>(), new Meta(count));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a new endpoint", description = "Creates a new endpoint by providing data such as a description, a name, and the endpoint properties. Use this endpoint to create endpoints for integration with third-party services such as webhooks, Slack, or Google Chat.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = Endpoint.class))),
        @APIResponse(responseCode = "400", description = "Bad data passed, that does not correspond to the definition or Endpoint.properties are empty")
    })
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    public EndpointDTO createEndpoint(
        @Context                                        SecurityContext sec,
        @RequestBody(required = true) @NotNull @Valid   EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        try {
            return this.endpointMapper.toDTO(
                this.internalCreateEndpoint(sec, endpoint)
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
     * @param sec the security context of the request.
     * @param endpoint the endpoint to be created.
     * @return the created endpoint in the database.
     */
    @Transactional
    public Endpoint internalCreateEndpoint(
            @Context                                        SecurityContext sec,
            @RequestBody(required = true) @NotNull @Valid   Endpoint endpoint
    )  {
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

        this.secretUtils.createSecretsForEndpoint(endpoint);

        return this.endpointRepository.createEndpoint(endpoint);
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public EndpointDTO getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec,
                     @RequestBody(required = true) @NotNull @Valid RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(
                getOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION)
        );
    }

    @POST
    @Path("/system/drawer_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a drawer endpoint", description = "Adds the drawer system endpoint into the system and specifies the role-based access control (RBAC) group that will receive notifications. Use this endpoint to add an animation as a notification in the UI.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public EndpointDTO getOrCreateDrawerSubscriptionEndpoint(@Context SecurityContext sec,
                                                         @RequestBody(required = true) @NotNull @Valid RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(
            getOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER)
        );
    }

    private Endpoint getOrCreateSystemSubscriptionEndpoint(SecurityContext sec, RequestSystemSubscriptionProperties requestProps, EndpointType endpointType) {
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
        Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);
        if (endpoint == null) {
            throw new NotFoundException();
        } else {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint);

            // Redact all the credentials from the endpoint's properties.
            this.redactSecretsForEndpoint(sec, endpoint);

            return this.endpointMapper.toDTO(endpoint);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Operation(summary = "Delete an endpoint", description = "Deletes an endpoint. Use this endpoint to delete an endpoint that is no longer needed. Deleting an endpoint that is already linked to a behavior group will unlink it from the behavior group. You cannot delete system endpoints.")
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Operation(summary = "Disable an endpoint", description = "Disables an endpoint so that the endpoint will not be executed after an operation that uses the endpoint is started. An operation that is already running can still execute the endpoint. Disable an endpoint when you want to stop it from running and might want to re-enable it in the future.")
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        checkSystemEndpoint(endpointType);
        endpointRepository.disableEndpoint(orgId, id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Operation(summary = "Update an endpoint", description = "Updates the endpoint configuration. Use this to update an existing endpoint. Any changes to the endpoint take place immediately.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateEndpoint(@Context SecurityContext sec,
                                   @PathParam("id") UUID id,
                                   @RequestBody(required = true) @NotNull @Valid EndpointDTO endpointDTO) {
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Operation(summary = "Retrieve event notification details", description = "Retrieves extended information about the outcome of an event notification related to the specified endpoint. Use this endpoint to learn why an event delivery failed.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
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
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    public void testEndpoint(@Context SecurityContext sec, @RestPath UUID uuid, @RequestBody @Valid final EndpointTestRequest requestBody) {
        final String orgId = SecurityContextUtil.getOrgId(sec);

        if (!this.endpointRepository.existsByUuidAndOrgId(uuid, orgId)) {
            throw new NotFoundException("integration not found");
        }

        final InternalEndpointTestRequest internalEndpointTestRequest = new InternalEndpointTestRequest();
        internalEndpointTestRequest.endpointUuid = uuid;
        internalEndpointTestRequest.orgId = orgId;
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
        // Only redact the secrets for those users who have read only permissions.
        if (!securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)) {
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
}
