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
import com.redhat.cloud.notifications.models.EndpointProperties;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
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

    @Inject
    EndpointRepository endpointRepository;

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
        checkSystemEndpoint(endpoint.getType());
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);

        if (endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        }

        if (isForOpenBridge(endpoint)) {
            /*
             * We need to include the endpoint id in the processor definition before the endpoint is persisted
             * on our side. That's why the id is generated here.
             */
            endpoint.setId(UUID.randomUUID());
            endpoint.setStatus(EndpointStatus.UNKNOWN);
            try {
                String bridgeId = bridge.getId();
                String token = bridgeAuth.getToken();
                CamelProperties cp = endpoint.getProperties(CamelProperties.class);
                cp.getExtras().put(OB_PROCESSOR_NAME, endpoint.getId().toString());

                UnaryOperator<Processor> execFun = in -> {
                    try {
                        return bridgeApiService.addProcessor(bridgeId, token, in);
                    } catch (WebApplicationException e) {
                        String path = "POST " + BASE_PATH + "/{bridgeId}/processors";
                        rhoseErrorMetricsRecorder.record(path, e);
                        throw e;
                    }
                };
                UnaryOperator<Processor> updateFun = in -> { endpoint.setStatus(EndpointStatus.PROVISIONING); return in; };
                Processor out = executeObAction(endpoint, updateFun, execFun);
                // We need to record the id of that processor, as we need it later

                cp.getExtras().put(OB_PROCESSOR_ID, out.getId());
            } catch (Exception e) {
                Log.error("Adding a processor failed ", e);
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
        checkSystemEndpoint(endpointType);

        Endpoint ep = endpointRepository.getEndpoint(orgId, id);
        if (isForOpenBridge(ep)) {

            try {
                UnaryOperator<Processor> execFun = in -> {
                    try {
                        bridgeApiService.deleteProcessor(bridge.getId(), in.getId(), bridgeAuth.getToken());
                    } catch (WebApplicationException e) {
                        String path = "DELETE " + BASE_PATH + "/{bridgeId}/processors/{processorId}";
                        rhoseErrorMetricsRecorder.record(path, e);
                        throw e;
                    }
                    return null;
                };
                ep.setStatus(EndpointStatus.DELETING);
                executeObAction(ep, null, execFun);
            } catch (Exception ex) {
                Log.warn("Deletion of processor failed: ", ex);
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

        Endpoint ep = endpointRepository.getEndpoint(orgId, id);
        if (isForOpenBridge(ep)) {

            // We need to save the processor name and id, as the incoming endpoint can't have them
            var ref = new Object() {
                String channel = null;
                String processorName;
                String processorId;
                String url;

            };
            EndpointProperties properties = ep.getProperties();
            if (properties instanceof CamelProperties) {
                CamelProperties cp = (CamelProperties) properties;

                ref.processorId = cp.getExtras().get(OB_PROCESSOR_ID);
                ref.processorName = cp.getExtras().get(OB_PROCESSOR_NAME);
                CamelProperties out = endpoint.getProperties(CamelProperties.class);
                out.getExtras().put(OB_PROCESSOR_NAME, ref.processorName);
                out.getExtras().put(OB_PROCESSOR_ID, ref.processorId);
                ref.channel = out.getExtras().get("channel");
                ref.url = cp.getUrl();
            }

            try {
                UnaryOperator<Processor> updateFun = in -> {
                    in.getAction().getParameters().put(SLACK_CHANNEL, ref.channel);
                    in.getAction().getParameters().put(SLACK_WEBHOOK_URL, ref.url);
                    return in;
                };
                UnaryOperator<Processor> execFun = in -> {
                    try {
                        return bridgeApiService.updateProcessor(bridge.getId(), in.getId(), bridgeAuth.getToken(), in);
                    } catch (WebApplicationException e) {
                        String path = "PUT " + BASE_PATH + "/{bridgeId}/processors/{processorId}";
                        rhoseErrorMetricsRecorder.record(path, e);
                        throw e;
                    }
                };
                ep.setStatus(EndpointStatus.PROVISIONING);
                Processor out = executeObAction(ep, updateFun, execFun);
                Log.infof("Endpoint updated to processor %s ", out.toString());

            } catch (Exception ex) {
                Log.error("Update of processor failed: ", ex);
                return Response.serverError().build();
            }
        }

        endpointRepository.updateEndpoint(endpoint);

        // Update the secrets in Sources.
        if (this.featureFlipper.isSourcesUsedAsSecretsBackend()) {
            var endpointProperties = endpoint.getProperties();
            var databaseEndpointProperties = ep.getProperties();

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

    /**
     * Helper that has some boilerplate code when dealing with OpenBridge, which
     * then in turn calls the provided functions to perform some work.
     * @param endpoint Endpoint definition to operate on
     * @param updateFunc A function that modifies the data structure of a created processor. Can be null
     * @param executeFunc A function that talks with the remote to perform an action
     * @return
     */
    Processor executeObAction(Endpoint endpoint, UnaryOperator<Processor> updateFunc, UnaryOperator<Processor> executeFunc) {
        if (endpoint != null) {
            EndpointProperties properties = endpoint.getProperties();
            if (properties != null && properties instanceof CamelProperties) {
                CamelProperties cp = (CamelProperties) properties;
                // Special case wrt OpenBridge
                if (endpoint.getSubType().equals(SLACK)) {
                    String processorId = cp.getExtras().get(OB_PROCESSOR_ID);
                    Processor p = createProcessor(endpoint);
                    if (processorId != null) {
                        p.setId(processorId);
                    }

                    // First apply changed to processor struct
                    Processor updated;
                    if (updateFunc != null) {
                        updated = updateFunc.apply(p);
                    } else {
                        updated = p;
                    }

                    // Then execute the API
                    Processor out;
                    if (executeFunc != null) {
                        out = executeFunc.apply(updated);
                    } else {
                        out = updated;
                    }

                    return out;
                }
            }
        }
        return null;
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

    private static void checkSystemEndpoint(EndpointType endpointType) {
        if (systemEndpointType.contains(endpointType)) {
            throw new BadRequestException(String.format(
                    "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                    endpointType
            ));
        }
    }


    /*
     * Creates a template processor object from the provided
     * endpoint.
     */
    @WithSpan
    Processor createProcessor(Endpoint endpoint) {

        CamelProperties properties = endpoint.getProperties(CamelProperties.class);
        String processorName = properties.getExtras().get(OB_PROCESSOR_NAME);
        String processorId = properties.getExtras().get(OB_PROCESSOR_ID);

        Processor out = new Processor(processorName);

        if (processorId != null) {
            out.setId(processorId);
        }

        List<Processor.Filter> filters = out.getFilters();
        filters.add(mkFilter("StringEquals", ORG_ID_FILTER_NAME, endpoint.getOrgId()));
        filters.add(mkFilter("StringEquals", OB_PROCESSOR_NAME, processorName));

        // The next lines are specific per integration type and need to
        // be changed accordingly for new types like Splunk or Tower or ...
        Processor.Action action = new Processor.Action(SLACK_ACTION);
        Map<String, Object> props = action.getParameters();
        props.put(SLACK_CHANNEL, properties.getExtras().getOrDefault("channel", "#general"));
        props.put(SLACK_WEBHOOK_URL, properties.getUrl());

        out.setAction(action);

        // Get a qute template. First ask for an account specific one. This will fall back to the default if needed.
        Optional<IntegrationTemplate> gTemplate = templateRepository.findIntegrationTemplate(null,
                endpoint.getOrgId(),
                IntegrationTemplate.TemplateKind.ORG,
                SLACK);

        String template;
        if (gTemplate.isPresent()) {
            template = gTemplate.get().getTheTemplate().getData();
        } else {
            throw new IllegalStateException("No default template defined for integration ");
        }
        out.setTransformationTemplate(template);
        return out;
    }

    private Processor.Filter mkFilter(String type, String key, String value) {
        Processor.Filter out = new Processor.Filter();
        out.setType(type);
        out.setKey(key);
        out.setValue(value);
        return out;
    }

    private boolean isForOpenBridge(Endpoint endpoint) {
        return featureFlipper.isObEnabled() &&
                endpoint != null &&
                endpoint.getType().equals(EndpointType.CAMEL) &&
                endpoint.getSubType() != null &&
                endpoint.getSubType().equals(SLACK);
    }
}
