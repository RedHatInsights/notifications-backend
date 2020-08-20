package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.UUID;

@Path("/endpoints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointService {

    @Inject
    EndpointResources resources;

    @Inject
    NotificationResources notifResources;

    @GET
    @RolesAllowed("read")
    public Multi<Endpoint> getEndpoints(@Context SecurityContext sec) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getEndpoints(principal.getAccount());
    }

    @POST
    @RolesAllowed("write")
    public Uni<Endpoint> createEndpoint(@Context SecurityContext sec, Endpoint endpoint) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        endpoint.setTenant(principal.getAccount());
        return resources.createEndpoint(endpoint);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("read")
    public Uni<Endpoint> getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        // TODO This should return with typed properties
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getEndpoint(principal.getAccount(), id);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("write")
    public Uni<Response> deleteEndpoint(@Context SecurityContext sec, @PathParam("id") String id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.deleteEndpoint(principal.getAccount(), id)
                // onFailure() ?
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}/enable")
    @RolesAllowed("write")
    public Uni<Response> enableEndpoint(@Context SecurityContext sec, @PathParam("id") String id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.enableEndpoint(principal.getAccount(), id)
                .onItem().apply(ignored -> Response.noContent().build());
    }

    @DELETE
    @Path("/{id}/enable")
    @RolesAllowed("write")
    public Uni<Response> disableEndpoint(@Context SecurityContext sec, @PathParam("id") String id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.disableEndpoint(principal.getAccount(), id)
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("write")
    public Uni<Response> updateEndpoint(@Context SecurityContext sec, @PathParam("id") String id, Endpoint endpoint) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return null; // TODO
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed("read")
    public Multi<NotificationHistory> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return notifResources.getNotificationHistory(principal.getAccount(), id);
    }

    @GET
    @Path("/{id}/history/{history_id}")
    @RolesAllowed("read")
    public Uni<Response> getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @PathParam("history_id") Integer historyId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return notifResources.getNotificationDetails(principal.getAccount(), id, historyId)
                .onItem().apply(json -> Response.ok(json).build());
    }
}
