package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeAuth;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointResource {

    public static final String OB_PROCESSOR_ID = "processorId";
    public static final String OB_PROCESSOR_NAME = "processorname"; // Must be all lower for the filter.
    public static final String SLACK = "Slack";

    private static final Logger LOGGER = Logger.getLogger(EndpointResource.class);

    private static final List<EndpointType> systemEndpointType = List.of(
            EndpointType.EMAIL_SUBSCRIPTION
    );

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @ConfigProperty(name = "ob.enabled", defaultValue = "false")
    boolean obEnabled;

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Inject
    Bridge bridge;

    @Inject
    BridgeAuth bridgeAuth;


    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Parameters({
        @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page, if not specified or 0 is used, returns all elements",
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
            @BeanParam Query query,
            @QueryParam("type") List<String> targetType,
            @QueryParam("active") Boolean activeOnly,
            @QueryParam("name") String name) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        List<Endpoint> endpoints;
        Long count;

        if (targetType != null && targetType.size() > 0) {
            Set<CompositeEndpointType> compositeType = targetType.stream().map(s -> {
                String[] pieces = s.split(":", 2);
                try {
                    if (pieces.length == 1) {
                        return new CompositeEndpointType(EndpointType.valueOf(s.toUpperCase()));
                    } else {
                        return new CompositeEndpointType(EndpointType.valueOf(pieces[0].toUpperCase()), pieces[1]);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown endpoint type: [" + s + "]", e);
                }
            }).collect(Collectors.toSet());
            endpoints = endpointRepository
                    .getEndpointsPerCompositeType(principal.getAccount(), name, compositeType, activeOnly, query);
            count = endpointRepository.getEndpointsCountPerCompositeType(principal.getAccount(), name, compositeType, activeOnly);
        } else {
            endpoints = endpointRepository.getEndpoints(principal.getAccount(), name, query);
            count = endpointRepository.getEndpointsCount(principal.getAccount(), name);
        }

        return new EndpointPage(endpoints, new HashMap<>(), new Meta(count));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint createEndpoint(@Context SecurityContext sec, @NotNull @Valid Endpoint endpoint) {
        checkSystemEndpoint(endpoint.getType());

        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setAccountId(principal.getAccount());

        if (endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        }

        if (obEnabled) {
            // TODO NOTIF-429 - see similar in EndpointResources#createEndpoint
            String endpointSubType;
            if (endpoint.getSubType() != null) {
                endpointSubType = endpoint.getSubType();
            } else {
                if (endpoint.getType() == EndpointType.CAMEL) {
                    endpointSubType = endpoint.getProperties(CamelProperties.class).getSubType();
                } else {
                    endpointSubType = "not-defined"; // No Camel endpoint, so we can skip
                }
            }

            if (endpointSubType != null && endpointSubType.equals("slack")) {
                CamelProperties properties = endpoint.getProperties(CamelProperties.class);
                String processorName = "p-" + endpoint.getAccountId() + "-" + UUID.randomUUID();
                properties.getExtras().put(OB_PROCESSOR_NAME, processorName);
                String processorId = null;
                try {
                    processorId = setupOpenBridgeProcessor(endpoint, properties, processorName);
                } catch (Exception e) {
                    LOGGER.warn("Processor setup failed: " + e.getMessage());
                    throw new InternalServerErrorException(e.getMessage());
                }

                // TODO find a better place for these, that should not be
                //       visible to users / OB actions
                //       See also CamelTypeProcessor#callOpenBridge
                properties.getExtras().put(OB_PROCESSOR_ID, processorId);

            }
        }

        return endpointRepository.createEndpoint(endpoint);
    }

    @POST
    @Path("/system/email_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid RequestEmailSubscriptionProperties requestProps) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        // Prevent from creating not public facing properties
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());

        return endpointRepository.getOrCreateEmailSubscriptionEndpoint(principal.getAccount(), properties);
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    public Endpoint getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        Endpoint endpoint = endpointRepository.getEndpoint(principal.getAccount(), id);
        if (endpoint == null) {
            throw new NotFoundException();
        } else {
            return endpoint;
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = endpointRepository.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);

        if (obEnabled) {
            Endpoint e = endpointRepository.getEndpoint(principal.getAccount(), id);
            if (e != null) {
                EndpointProperties properties = e.getProperties();
                if (properties instanceof CamelProperties) {
                    CamelProperties cp = (CamelProperties) properties;
                    // Special case wrt OpenBridge
                    if (e.getSubType().equals("slack")) {
                        String processorId = cp.getExtras().get(OB_PROCESSOR_ID);
                        if (processorId != null) { // Should not be null under normal operations.
                            try {
                                bridgeApiService.deleteProcessor(bridge.getId(), processorId, bridgeAuth.getToken());
                            } catch (Exception ex) {
                                LOGGER.warn("Removal of OB processor failed:" + ex.getMessage());
                                // Nothing more we can do
                            }
                        }
                    }
                }
            }
        }

        endpointRepository.deleteEndpoint(principal.getAccount(), id);

        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = endpointRepository.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);
        endpointRepository.enableEndpoint(principal.getAccount(), id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/enable")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = endpointRepository.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);
        endpointRepository.disableEndpoint(principal.getAccount(), id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id, @NotNull @Valid Endpoint endpoint) {
        // This prevents from updating an endpoint from whatever EndpointType to a system EndpointType
        checkSystemEndpoint(endpoint.getType());
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setAccountId(principal.getAccount());
        endpoint.setId(id);

        EndpointType endpointType = endpointRepository.getEndpointTypeById(principal.getAccount(), id);
        // This prevents from updating an endpoint from system EndpointType to a whatever EndpointType
        checkSystemEndpoint(endpointType);
        endpointRepository.updateEndpoint(endpoint);
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
    public List<NotificationHistory> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @BeanParam Query query) {
        // TODO We need globally limitations (Paging support and limits etc)
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        boolean doDetail = includeDetail != null && includeDetail;
        return notificationRepository.getNotificationHistory(principal.getAccount(), id, doDetail, query);
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        JsonObject json = notificationRepository.getNotificationDetails(principal.getAccount(), endpointId, historyId);
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

        Application app = applicationRepository.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionRepository.subscribe(
                    principal.getAccount(),
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

        Application app = applicationRepository.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionRepository.unsubscribe(
                    principal.getAccount(),
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

    private String setupOpenBridgeProcessor(Endpoint endpoint, CamelProperties properties, String processorName) {

        Map<String, Object> out = new HashMap<>();
        out.put("name", processorName);

        List<Object> filters = new ArrayList<>();
        out.put("filters", filters);
        filters.add(mkFilter("StringEquals", "rhaccount", endpoint.getAccountId()));
        filters.add(mkFilter("StringEquals", OB_PROCESSOR_NAME, processorName));

        Map<String, Object> actionMap = new HashMap<>();
        out.put("action", actionMap);

        actionMap.put("type", SLACK); // needs to be literally like this to the Slack Action

        // A qute template we should get it from DB
        String template = "Hello from *Notifications* via _OpenBridge_ with {data.events.size()} event{#if data.events.size() > 1}s{/} from Application _{data.application}_ in Bundle _{data.bundle}_\n" +
                "Events: {data.events} \n" +
                "{#if data.context.size() > 0} Context is:\n" +
                "{#each data.context}*{it.key}* -> _{it.value}_\n" +
                "{/each}{/if}\n" +
                "Brought to you by :redhat:\n";
        out.put("transformationTemplate", template);

        Map<String, String> props = new HashMap<>();
        props.put("channel", properties.getExtras().getOrDefault("channel", "#general"));
        props.put("webhookUrl", properties.getUrl());
        actionMap.put("parameters", props);

        String token = bridgeAuth.getToken();

        Map<String, Object> pInfo = bridgeApiService.addProcessor(bridge.getId(), token, out);

        String processorId = (String) pInfo.get("id");

        return processorId;

    }

    private Map<String, String> mkFilter(String type, String key, String value) {
        Map<String, String> out = new HashMap<>(3);
        out.put("type", type);
        out.put("key", key);
        out.put("value", value);
        return out;
    }

}
