package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.EndpointResourcesJDBC;
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
import java.net.URI;

@Path("/endpoints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
// TODO Needs a filter or preprocessor to get x-rh-identity parsed @Context SecurityContext ctx and then tenant set correctly
// Email endpoints are not added at this point
public class EndpointService {

    @Inject
    EndpointResources resources;

    @Inject
    EndpointResourcesJDBC resdb;

    @GET
    public Multi<Endpoint> getEndpoints() {
        return resdb.getEndpoints("tenant");
    }

    @POST
    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
        endpoint.setTenant("tenant");
        return resdb.createEndpoint(endpoint);
    }

    @GET
    @Path("/{id}")
    public Uni<Endpoint> getEndpoint(@PathParam("id") String id) {
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
    @Path("/{id}")
    public Uni<Response> updateEndpoint(@PathParam("id") String id) {
        return null; // TODO
    }

    @GET
    @Path("/{id}/history")
    public Multi<NotificationHistory> getEndpointHistory(@PathParam("id") String id) {
        return null; // TODO
    }

    @GET
    @Path("/{id}/history/{history_id}")
    // TODO This should return a non-typed JSON
    public Uni<Response> getDetailedEndpointHistory(@PathParam("id") String id, @PathParam("history_id") String historyId) {
        return null;
    }
}
