package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rbac.RbacGroupValidator;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
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

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointResource {

    public static final String EMPTY_SLACK_CHANNEL_ERROR = "The channel field is required";
    public static final String UNSUPPORTED_ENDPOINT_TYPE = "Unsupported endpoint type";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    @RestClient
    EndpointTestService endpointTestService;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    RbacGroupValidator rbacGroupValidator;

    @Inject
    FeatureFlipper featureFlipper;

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
    @Operation(summary = "List endpoints", description = "Get a list of endpoints filtered down by the passed parameters.")
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

        for (Endpoint endpoint: endpoints) {
            // Fetch the secrets from Sources.
            if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
                this.secretUtils.loadSecretsForEndpoint(endpoint);
            }
        }

        return new EndpointPage(endpoints, new HashMap<>(), new Meta(count));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a new endpoint", description = "Create a new endpoint from the passed data")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = Endpoint.class))),
        @APIResponse(responseCode = "400", description = "Bad data passed, that does not correspond to the definition or Endpoint.properties are empty")
    })
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint createEndpoint(@Context SecurityContext sec,
                                   @RequestBody(required = true) @NotNull @Valid Endpoint endpoint) {
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

        if (endpoint.getType() == CAMEL && "slack".equals(endpoint.getSubType())) {
            checkSlackChannel(endpoint.getProperties(CamelProperties.class));
        }

        endpoint.setStatus(EndpointStatus.READY);

        /*
         * Create the secrets in Sources.
         *
         * TODO: if there's a failure in the "createEndpoint" function below,
         * we might end up with dangling secrets in Sources. Using a "try/catch"
         * block wouldn't do it here because of the "@Transactional" annotation
         * above. Check <a href="https://issues.redhat.com/browse/RHCLOUD-22971">RHCLOUD-22971</a>
         * for more details.
         */
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            this.secretUtils.createSecretsForEndpoint(endpoint);
        }

        return endpointRepository.createEndpoint(endpoint);
    }

    private String checkSlackChannel(CamelProperties camelProperties) {
        String channel = camelProperties.getExtras().get("channel");
        if (channel == null || channel.isBlank()) {
            throw new BadRequestException(EMPTY_SLACK_CHANNEL_ERROR);
        }
        return channel;
    }

    @POST
    @Path("/system/email_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec,
                     @RequestBody(required = true) @NotNull @Valid RequestSystemSubscriptionProperties requestProps) {
        return getOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION);
    }

    @POST
    @Path("/system/drawer_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint getOrCreateDrawerSubscriptionEndpoint(@Context SecurityContext sec,
                                                         @RequestBody(required = true) @NotNull @Valid RequestSystemSubscriptionProperties requestProps) {
        return getOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER);
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
    public Endpoint getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        String orgId = getOrgId(sec);
        Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);
        if (endpoint == null) {
            throw new NotFoundException();
        } else {
            // Fetch the secrets from Sources.
            if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
                this.secretUtils.loadSecretsForEndpoint(endpoint);
            }

            return endpoint;
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
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
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            Endpoint ep = endpointRepository.getEndpoint(orgId, id);
            this.secretUtils.deleteSecretsForEndpoint(ep);
        }

        endpointRepository.deleteEndpoint(orgId, id);

        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
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
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateEndpoint(@Context SecurityContext sec,
                                   @PathParam("id") UUID id,
                                   @RequestBody(required = true) @NotNull @Valid Endpoint endpoint) {
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

        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        // This prevents from updating an endpoint from system EndpointType to a whatever EndpointType
        checkSystemEndpoint(endpointType);

        if (endpoint.getType() == CAMEL && "slack".equals(endpoint.getSubType())) {
            checkSlackChannel(endpoint.getProperties(CamelProperties.class));
        }

        endpointRepository.updateEndpoint(endpoint);

        // Update the secrets in Sources.
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, id);
            var endpointProperties = endpoint.getProperties();
            var databaseEndpointProperties = dbEndpoint.getProperties();

            if (endpointProperties instanceof SourcesSecretable && databaseEndpointProperties instanceof SourcesSecretable) {
                // In order to be able to update the secrets in Sources, we need to grab the IDs of these secrets from the
                // database endpoint, since the client won't be sending those IDs.
                var incomingEndpointProps = (SourcesSecretable) endpointProperties;
                var databaseEndpointProps = (SourcesSecretable) databaseEndpointProperties;

                databaseEndpointProps.setBasicAuthentication(incomingEndpointProps.getBasicAuthentication());
                databaseEndpointProps.setSecretToken(incomingEndpointProps.getSecretToken());

                this.secretUtils.updateSecretsForEndpoint(dbEndpoint);
            }
        }

        return Response.ok().build();
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
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

    @Deprecated
    @PUT
    @Path("/email/subscription/{bundleName}/{applicationName}/{type}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public boolean subscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        if (!featureFlipper.isInstantEmailsEnabled() && type == INSTANT) {
            throw new BadRequestException("Subscribing to instant emails is not supported");
        }

        Application app = applicationRepository.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionRepository.subscribe(
                accountId,
                orgId,
                principal.getName(),
                bundleName,
                applicationName,
                type
            );
        }
    }

    @Deprecated
    @DELETE
    @Path("/email/subscription/{bundleName}/{applicationName}/{type}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public boolean unsubscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String orgId = getOrgId(sec);

        if (!featureFlipper.isInstantEmailsEnabled() && type == INSTANT) {
            throw new BadRequestException("Unsubscribing from instant emails is not supported");
        }

        Application app = applicationRepository.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionRepository.unsubscribe(
                orgId,
                principal.getName(),
                bundleName,
                applicationName,
                type
            );
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
        return !featureFlipper.isEmailsOnlyMode() || endpointType.isSystemEndpointType;
    }
}
