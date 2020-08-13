package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/endpoints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// TODO Needs a filter or preprocessor to get x-rh-identity parsed @Context SecurityContext ctx and then set tenant correctly
// Email endpoints are not added at this point
// TODO Needs documentation annotations also
public class EndpointService {

//    @Inject
//    RhIdPrincipal user;

    @Inject
    EndpointResources resources;

    @Inject
    NotificationResources notifResources;

    @GET
    public Multi<Endpoint> getEndpoints() {
        return resources.getEndpoints("tenant");
    }

    @POST
    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
        endpoint.setTenant("tutorial");
        return resources.createEndpoint(endpoint);
    }

    @GET
    @Path("/{id}")
    public Uni<Endpoint> getEndpoint(@PathParam("id") String id) {
//        if (!user.canReadAll()) {
//            throw new RuntimeException("no permission");
//        }
        // TODO This should return with typed properties
        return resources.getEndpoint("tenant", id);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteEndpoint(@PathParam("id") String id) {
        return resources.deleteEndpoint("tenant", id)
                // onFailure() ?
                .onItem().apply(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}/enable")
    public Uni<Response> enableEndpoint(@PathParam("id") String id) {
        return resources.enableEndpoint("tenant", id)
                .onItem().apply(ignored -> Response.noContent().build());
    }

    @DELETE
    @Path("/{id}/enable")
    public Uni<Response> disableEndpoint(@PathParam("id") String id) {
        return resources.disableEndpoint("tenant", id)
                .onItem().apply(ignored -> Response.noContent().build());
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateEndpoint(@PathParam("id") String id, Endpoint endpoint) {
        return null; // TODO
    }

    @GET
    @Path("/{id}/history")
    public Multi<NotificationHistory> getEndpointHistory(@PathParam("id") UUID id) {
        return notifResources.getNotificationHistory("tenant", id);
    }

    @GET
    @Path("/{id}/history/{history_id}")
    public Uni<Response> getDetailedEndpointHistory(@PathParam("id") UUID id, @PathParam("history_id") Integer historyId) {
        return notifResources.getNotificationDetails("tenant", id, historyId)
                .onItem().apply(json -> Response.ok(json).build());
    }
}
