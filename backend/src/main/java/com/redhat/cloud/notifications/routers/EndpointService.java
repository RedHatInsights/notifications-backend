package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.RequestEmailSubscriptionProperties;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

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
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.db.NotificationResources.MAX_NOTIFICATION_HISTORY_RESULTS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointService {

    private static final List<EndpointType> systemEndpointType = List.of(
            EndpointType.EMAIL_SUBSCRIPTION
    );

    @Inject
    EndpointResources resources;

    @Inject
    NotificationResources notifResources;

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    ApplicationResources applicationResources;

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
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
            @QueryParam("active") Boolean activeOnly) {
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
            endpoints = resources
                    .getEndpointsPerCompositeType(principal.getAccount(), compositeType, activeOnly, query);
            count = resources.getEndpointsCountPerCompositeType(principal.getAccount(), compositeType, activeOnly);
        } else {
            endpoints = resources.getEndpoints(principal.getAccount(), query);
            count = resources.getEndpointsCount(principal.getAccount());
        }

        return new EndpointPage(endpoints, new HashMap<>(), new Meta(count));
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint createEndpoint(@Context SecurityContext sec, @NotNull @Valid Endpoint endpoint) {
        checkSystemEndpoint(endpoint.getType());

        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setAccountId(principal.getAccount());

        if (endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        }

        return resources.createEndpoint(endpoint);
    }

    @POST
    @Path("/system/email_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public Endpoint getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid RequestEmailSubscriptionProperties requestProps) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        // Prevent from creating not public facing properties
        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());

        return resources.getOrCreateEmailSubscriptionEndpoint(principal.getAccount(), properties);
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    public Endpoint getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        Endpoint endpoint = resources.getEndpoint(principal.getAccount(), id);
        if (endpoint == null) {
            throw new NotFoundException();
        } else {
            return endpoint;
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = resources.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);
        resources.deleteEndpoint(principal.getAccount(), id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = resources.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);
        resources.enableEndpoint(principal.getAccount(), id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/enable")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        EndpointType endpointType = resources.getEndpointTypeById(principal.getAccount(), id);
        checkSystemEndpoint(endpointType);
        resources.disableEndpoint(principal.getAccount(), id);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id, @NotNull @Valid Endpoint endpoint) {
        // This prevents from updating an endpoint from whatever EndpointType to a system EndpointType
        checkSystemEndpoint(endpoint.getType());
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setAccountId(principal.getAccount());
        endpoint.setId(id);

        EndpointType endpointType = resources.getEndpointTypeById(principal.getAccount(), id);
        // This prevents from updating an endpoint from system EndpointType to a whatever EndpointType
        checkSystemEndpoint(endpointType);
        resources.updateEndpoint(endpoint);
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

    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    public List<NotificationHistory> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @BeanParam Query query) {
        // TODO We need globally limitations (Paging support and limits etc)
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        boolean doDetail = includeDetail != null && includeDetail;
        return notifResources.getNotificationHistory(principal.getAccount(), id, doDetail, query);
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        JsonObject json = notifResources.getNotificationDetails(principal.getAccount(), endpointId, historyId);
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
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public boolean subscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        Application app = applicationResources.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionResources.subscribe(
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
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @Transactional
    public boolean unsubscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        Application app = applicationResources.getApplication(bundleName, applicationName);
        if (app == null) {
            throw new NotFoundException();
        } else {
            return emailSubscriptionResources.unsubscribe(
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

}
