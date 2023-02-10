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
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.openbridge.Processor;
import com.redhat.cloud.notifications.openbridge.RhoseErrorMetricsRecorder;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.logging.Log;
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
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ORG;
import static com.redhat.cloud.notifications.openbridge.BridgeApiService.BASE_PATH;
import static com.redhat.cloud.notifications.openbridge.BridgeHelper.ORG_ID_FILTER_NAME;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointResource {

    public static final String OB_PROCESSOR_ID = "processorId";
    public static final String OB_PROCESSOR_NAME = "processorname"; // Must be all lower for the filter.
    public static final String SLACK_ACTION = "slack_sink_0.1";

    private static final List<EndpointType> systemEndpointType = List.of(
            EndpointType.EMAIL_SUBSCRIPTION
    );
    public static final String SLACK_CHANNEL = "slack_channel";
    public static final String SLACK = "slack";
    public static final String SLACK_WEBHOOK_URL = "slack_webhook_url";
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
    TemplateRepository templateRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;

    @Inject
    RhoseErrorMetricsRecorder rhoseErrorMetricsRecorder;

    /**
     * Used to create the secrets in Sources and update the endpoint's properties' IDs.
     */
    @Inject
    SecretUtils secretUtils;

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
            if (isForOpenBridge(endpoint)) {
                // Don't return the processor info, it is internal only
                CamelProperties cp = endpoint.getProperties(CamelProperties.class);
                cp.getExtras().remove(OB_PROCESSOR_ID);
                cp.getExtras().remove(OB_PROCESSOR_NAME);
            }

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

        if (isForOpenBridge(endpoint)) {
            CamelProperties cp = endpoint.getProperties(CamelProperties.class);
            String slackChannel = checkSlackChannel(cp);

            /*
             * We need to include the endpoint id in the processor definition before the endpoint is persisted
             * on our side. That's why the id is generated here.
             */
            endpoint.setId(UUID.randomUUID());
            endpoint.setStatus(EndpointStatus.PROVISIONING);

            String processorName = endpoint.getId().toString();
            cp.getExtras().put(OB_PROCESSOR_NAME, processorName);

            try {
                Processor processor = buildProcessor(orgId, processorName, slackChannel, cp.getUrl());

                try {
                    Processor createdProcessor = bridgeApiService.addProcessor(bridge.getId(), bridgeAuth.getToken(), processor);
                    // We need to record the id of that processor, as we need it later
                    cp.getExtras().put(OB_PROCESSOR_ID, createdProcessor.getId());
                } catch (WebApplicationException e) {
                    String path = "POST " + BASE_PATH + "/{bridgeId}/processors";
                    rhoseErrorMetricsRecorder.record(path, e);
                    throw e;
                }
            } catch (Exception e) {
                Log.error("RHOSE processor creation failed", e);
                throw new InternalServerErrorException();
            }
        } else {
            endpoint.setStatus(EndpointStatus.READY);
        }

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
                     @RequestBody(required = true) @NotNull @Valid RequestEmailSubscriptionProperties requestProps) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        if (requestProps.getGroupId() != null && requestProps.isOnlyAdmins()) {
            throw new BadRequestException("Cannot use RBAC groups and only admins in the same endpoint");
        }

        if (requestProps.getGroupId() != null) {
            boolean isValid = rbacGroupValidator.validate(requestProps.getGroupId(), principal.getIdentity().rawIdentity);
            if (!isValid) {
                throw new BadRequestException(String.format("Invalid RBAC group identified with id %s", requestProps.getGroupId()));
            }
        }

        // Prevent from creating not public facing properties
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());
        properties.setGroupId(requestProps.getGroupId());

        return endpointRepository.getOrCreateEmailSubscriptionEndpoint(accountId, orgId, properties);
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
            if (isForOpenBridge(endpoint)) {
                // Don't return the processor info, it is internal only
                CamelProperties cp = endpoint.getProperties(CamelProperties.class);
                cp.getExtras().remove(OB_PROCESSOR_ID);
                cp.getExtras().remove(OB_PROCESSOR_NAME);
            }

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

        Endpoint ep = endpointRepository.getEndpoint(orgId, id);
        if (isForOpenBridge(ep)) {

            try {
                ep.setStatus(EndpointStatus.DELETING);
                CamelProperties properties = ep.getProperties(CamelProperties.class);
                String processorId = properties.getExtras().get(OB_PROCESSOR_ID);
                try {
                    bridgeApiService.deleteProcessor(bridge.getId(), processorId, bridgeAuth.getToken());
                } catch (WebApplicationException e) {
                    String path = "DELETE " + BASE_PATH + "/{bridgeId}/processors/{processorId}";
                    rhoseErrorMetricsRecorder.record(path, e);
                    throw e;
                }
            } catch (Exception e) {
                Log.warn("RHOSE processor deletion failed", e);
            }
        } else {
            endpointRepository.deleteEndpoint(orgId, id);
        }

        // Clean up the secrets in Sources.
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            this.secretUtils.deleteSecretsForEndpoint(ep);
        }

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

        Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, id);
        if (isForOpenBridge(dbEndpoint)) {

            CamelProperties requestProperties = endpoint.getProperties(CamelProperties.class);
            String slackChannel = checkSlackChannel(requestProperties);

            dbEndpoint.setStatus(EndpointStatus.PROVISIONING);

            CamelProperties dbProperties = dbEndpoint.getProperties(CamelProperties.class);
            dbProperties.getExtras().put("channel", slackChannel);
            dbProperties.setUrl(requestProperties.getUrl());

            String processorId = dbProperties.getExtras().get(OB_PROCESSOR_ID);
            String processorName = dbProperties.getExtras().get(OB_PROCESSOR_NAME);

            try {
                Processor processor = buildProcessor(orgId, processorName, slackChannel, requestProperties.getUrl());
                processor.setId(processorId);

                try {
                    Processor updatedProcessor = bridgeApiService.updateProcessor(bridge.getId(), processorId, bridgeAuth.getToken(), processor);
                    Log.infof("Endpoint updated to processor %s ", updatedProcessor.toString());
                } catch (WebApplicationException e) {
                    String path = "PUT " + BASE_PATH + "/{bridgeId}/processors/{processorId}";
                    rhoseErrorMetricsRecorder.record(path, e);
                    throw e;
                }
            } catch (Exception e) {
                Log.error("RHOSE processor update failed", e);
                return Response.serverError().build();
            }

            endpointRepository.updateEndpoint(dbEndpoint);
        } else {
            endpointRepository.updateEndpoint(endpoint);
        }

        // Update the secrets in Sources.
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            var endpointProperties = endpoint.getProperties();
            var databaseEndpointProperties = dbEndpoint.getProperties();

            if (endpointProperties instanceof SourcesSecretable && databaseEndpointProperties instanceof SourcesSecretable) {
                // In order to be able to update the secrets in Sources, we need to grab the IDs of these secrets from the
                // database endpoint, since the client won't be sending those IDs.
                var incomingEndpointProps = (SourcesSecretable) endpointProperties;
                var databaseEndpointProps = (SourcesSecretable) databaseEndpointProperties;

                incomingEndpointProps.setBasicAuthenticationSourcesId(databaseEndpointProps.getBasicAuthenticationSourcesId());
                incomingEndpointProps.setSecretTokenSourcesId(databaseEndpointProps.getSecretTokenSourcesId());

                this.secretUtils.updateSecretsForEndpoint(endpoint);
            }
        }

        return Response.ok().build();
    }

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
    public void testEndpoint(@Context SecurityContext sec, @RestPath UUID uuid) {
        final String orgId = SecurityContextUtil.getOrgId(sec);

        if (!this.endpointRepository.existsByUuidAndOrgId(uuid, orgId)) {
            throw new NotFoundException("integration not found");
        }

        final var endpointTestRequest = new EndpointTestRequest(uuid, orgId);

        this.endpointTestService.testEndpoint(endpointTestRequest);
    }

    private static void checkSystemEndpoint(EndpointType endpointType) {
        if (systemEndpointType.contains(endpointType)) {
            throw new BadRequestException(String.format(
                    "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                    endpointType
            ));
        }
    }

    @WithSpan
    Processor buildProcessor(String orgId, String name, String slackChannel, String slackWebhookUrl) {

        Processor.Action action = new Processor.Action(SLACK_ACTION);
        action.getParameters().put(SLACK_CHANNEL, slackChannel);
        action.getParameters().put(SLACK_WEBHOOK_URL, slackWebhookUrl);

        // Get a qute template. First ask for an account specific one. This will fall back to the default if needed.
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(null, orgId, ORG, SLACK)
                .orElseThrow(() -> new IllegalStateException("No default template defined for integration"));
        String template = integrationTemplate.getTheTemplate().getData();

        Processor processor = new Processor(name);
        processor.getFilters().add(mkFilter("StringEquals", ORG_ID_FILTER_NAME, orgId));
        processor.getFilters().add(mkFilter("StringEquals", OB_PROCESSOR_NAME, name));
        processor.setAction(action);
        processor.setTransformationTemplate(template);

        return processor;
    }

    private Processor.Filter mkFilter(String type, String key, String value) {
        Processor.Filter out = new Processor.Filter();
        out.setType(type);
        out.setKey(key);
        out.setValue(value);
        return out;
    }

    private boolean isForOpenBridge(Endpoint endpoint) {
        return endpoint != null &&
                endpoint.getType().equals(EndpointType.CAMEL) &&
                endpoint.getSubType() != null &&
                endpoint.getSubType().equals(SLACK);
    }

    private boolean isEndpointTypeAllowed(EndpointType endpointType) {
        return !featureFlipper.isEmailsOnlyMode() || endpointType == EndpointType.EMAIL_SUBSCRIPTION;
    }
}
