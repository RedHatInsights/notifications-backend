package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.UUID;

@Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointService {

    @Inject
    EndpointResources resources;

    @Inject
    NotificationResources notifResources;

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    ApplicationResources applicationResources;

    @GET
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
    public Uni<EndpointPage> getEndpoints(@Context SecurityContext sec, @BeanParam Query query, @QueryParam("type") String targetType, @QueryParam("active") Boolean activeOnly) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        Multi<Endpoint> endpoints;
        Uni<Integer> count;

        if (targetType != null) {
            EndpointType endpointType = EndpointType.valueOf(targetType.toUpperCase());
            endpoints = resources
                    .getEndpointsPerType(principal.getAccount(), endpointType, activeOnly, query);
            count = resources.getEndpointsCountPerType(principal.getAccount(), endpointType, activeOnly);
        } else {
            endpoints = resources.getEndpoints(principal.getAccount(), query);
            count = resources.getEndpointsCount(principal.getAccount());
        }

        return endpoints.collectItems().asList()
                .onItem().transformToUni(endpointsList -> count
                        .onItem().transform(endpointsCount -> new EndpointPage(endpointsList, new HashMap<>(), new Meta(endpointsCount))));
    }

    @POST
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    public Uni<Endpoint> createEndpoint(@Context SecurityContext sec, @NotNull @Valid Endpoint endpoint) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setTenant(principal.getAccount());

        if (endpoint.getType() != EndpointType.DEFAULT && endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        } else if (endpoint.getType() == EndpointType.DEFAULT) {
            // Only a single default endpoint is allowed
            return resources.getEndpointsPerType(principal.getAccount(), EndpointType.DEFAULT, null, null)
                    .toUni()
                    .onItem()
                    .ifNull()
                    .switchTo(resources.createEndpoint(endpoint));
        }

        return resources.createEndpoint(endpoint);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    public Uni<Endpoint> getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getEndpoint(principal.getAccount(), id)
                .onItem().ifNull().failWith(new NotFoundException());
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.deleteEndpoint(principal.getAccount(), id)
                // onFailure() ?
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}/enable")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.enableEndpoint(principal.getAccount(), id)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @DELETE
    @Path("/{id}/enable")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.disableEndpoint(principal.getAccount(), id)
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> updateEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id, @NotNull @Valid Endpoint endpoint) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setTenant(principal.getAccount());
        endpoint.setId(id);
        return resources.updateEndpoint(endpoint)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    public Multi<NotificationHistory> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id) {
        // TODO We need globally limitations (Paging support and limits etc)
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return notifResources.getNotificationHistory(principal.getAccount(), id);
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    @Parameters({
            @Parameter(
                    name = "pageSize",
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
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @PathParam("history_id") Integer historyId, @BeanParam Query query) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return notifResources.getNotificationDetails(principal.getAccount(), query, id, historyId)
                // Maybe 404 should only be returned if history_id matches nothing? Otherwise 204
                .onItem().ifNull().failWith(new NotFoundException())
                .onItem().transform(json -> {
                    if (json.isEmpty()) {
                        return Response.noContent().build();
                    }
                    return Response.ok(json).build();
                });
    }

    @PUT
    @Path("/email/subscription/{bundleName}/{applicationName}/{type}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    public Uni<Boolean> subscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        return applicationResources.getApplication(bundleName, applicationName)
                .onItem().ifNull().failWith(new NotFoundException())
                .onItem().transformToUni(application -> emailSubscriptionResources.subscribe(
                        principal.getAccount(),
                        principal.getName(),
                        bundleName,
                        applicationName,
                        type
                ));
    }

    @DELETE
    @Path("/email/subscription/{bundleName}/{applicationName}/{type}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS)
    public Uni<Boolean> unsubscribeEmail(
            @Context SecurityContext sec, @PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName,
            @PathParam("type") EmailSubscriptionType type) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        return applicationResources.getApplication(bundleName, applicationName)
                .onItem().ifNull().failWith(new NotFoundException())
                .onItem().transformToUni(application -> emailSubscriptionResources.unsubscribe(
                        principal.getAccount(),
                        principal.getName(),
                        bundleName,
                        applicationName,
                        type
                ));
    }

}
