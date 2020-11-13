package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotificationService {

    @Inject
    Vertx vertx;

    @Inject
    ApplicationResources apps;

    @Inject
    EndpointService endpointService;

    @Inject
    EndpointResources resources;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("/updates")
    public Multi<Notification> getNotificationUpdates(@Context SecurityContext sec) {
        // TODO Check the Notification type if we want something else
        // TODO Process:
        //      - Fetch the last unread notifications (with some limit?)
        //         - If we're not previously subscribed, add the last n-days of notifications to our unread list?
        //          - Add subscription to our subscription base to receive future notifications
        //      - Subscribe to VertX eventBus listening for: notifications<tenantId><userId>
        return vertx.eventBus().consumer(getAddress(sec.getUserPrincipal()))
                .toMulti()
                // TODO Verify that toMulti subscribes to a hot stream, not cold!
                .onItem()
                .transform(m -> (Notification) m.body());
    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> markRead(@Context SecurityContext sec, Integer id) {
        // Mark the notification id for <tenantId><userId> 's subscription as read
        return Uni.createFrom().nullItem();
    }

    // TODO Mark all as read?

    // TODO DB structure? <tenantId><userId><notificationId><read> ? Will we show old read-messages or not? Do we vacuum old items from the subscriptions?

    private String getAddress(Principal principal) {
        // TODO This should call some global point which is used by the Processor interface to push data to the same queue names
        RhIdPrincipal rhUser = (RhIdPrincipal) principal;
        return String.format("notifications-%s", rhUser.getAccount());
    }

    // Event type linking

    @GET
    @Path("/eventTypes")
    @RolesAllowed("read")
    public List<EventType> getEventTypes(@BeanParam Query query, @QueryParam("applicationId") UUID applicationId) {
        return apps.getEventTypes(query, applicationId).collectItems().asList().await().indefinitely();
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/{endpointId}")
    @RolesAllowed("write")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> linkEndpointToEventType(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId, @PathParam("eventTypeId") Integer eventTypeId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.linkEndpoint(principal.getAccount(), endpointId, eventTypeId)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}/{endpointId}")
    @RolesAllowed("write")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> unlinkEndpointFromEventType(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId, @PathParam("eventTypeId") Integer eventTypeId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.unlinkEndpoint(principal.getAccount(), endpointId, eventTypeId)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @GET
    @Path("/eventTypes/{eventTypeId}")
    @RolesAllowed("read")
    public List<Endpoint> getLinkedEndpoints(@Context SecurityContext sec, @PathParam("eventTypeId") Integer eventTypeId, @BeanParam Query query) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getLinkedEndpoints(principal.getAccount(), eventTypeId, query).collectItems().asList().await().indefinitely();
    }

    @GET
    @Path("/defaults")
    @RolesAllowed("read")
    public List<Endpoint> getEndpointsForDefaults(@Context SecurityContext sec) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getDefaultEndpoints(principal.getAccount()).collectItems().asList().await().indefinitely();
    }

    @PUT
    @Path("/defaults/{endpointId}")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> addEndpointToDefaults(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.addEndpointToDefaults(principal.getAccount(), endpointId)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @DELETE
    @Path("/defaults/{endpointId}")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> deleteEndpointFromDefaults(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.deleteEndpointFromDefaults(principal.getAccount(), endpointId)
                .onItem().transform(ignored -> Response.ok().build());
    }
}
