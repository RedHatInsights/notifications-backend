package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Endpoint.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
import java.util.Set;
import java.util.UUID;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotificationService {

    @Inject
    Vertx vertx;

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources apps;

    @Inject
    EndpointResources resources;

// We do not yet use this API yet and the return type is polluting the openapi with "internal details" because we are
// directly using an avro generated code.
//    @GET
//    @Produces(MediaType.SERVER_SENT_EVENTS)
//    @Path("/updates")
//    public Multi<Notification> getNotificationUpdates(@Context SecurityContext sec) {
//        // TODO Check the Notification type if we want something else
//        // TODO Process:
//        //      - Fetch the last unread notifications (with some limit?)
//        //         - If we're not previously subscribed, add the last n-days of notifications to our unread list?
//        //          - Add subscription to our subscription base to receive future notifications
//        //      - Subscribe to VertX eventBus listening for: notifications<tenantId><userId>
//        return vertx.eventBus().consumer(getAddress(sec.getUserPrincipal()))
//                .toMulti()
//                // TODO Verify that toMulti subscribes to a hot stream, not cold!
//                .onItem()
//                .transform(m -> (Notification) m.body());
//    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "No Content", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> markRead(@Context SecurityContext sec, @PathParam("id") Integer id) {
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
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Multi<EventType> getEventTypes(@BeanParam Query query, @QueryParam("applicationIds") Set<UUID> applicationIds, @QueryParam("bundleId") UUID bundleId) {
        return apps.getEventTypes(query, applicationIds, bundleId);
    }

    @GET
    @Path("/eventTypes/affectedByRemovalOfEndpoint/{endpointId}")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Multi<EventType> getEventTypesAffectedByEndpointId(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();

        Multi<EventType> directlyAffected = apps.getEventTypesByEndpointId(principal.getAccount(), endpointId);
        Multi<EventType> indirectlyAffected = resources.getEndpointsPerType(principal.getAccount(), EndpointType.DEFAULT, null, null).toUni().onItem().transformToMulti(defaultEndpoint ->
            resources.endpointInDefaults(principal.getAccount(), endpointId)
                    .onItem().transformToMulti(exists -> {
                        if (exists) {
                            return apps.getEventTypesByEndpointId(principal.getAccount(), defaultEndpoint.getId());
                        }

                        return Multi.createFrom().empty();
                    })
        );

        return Multi.createBy().merging().streams(directlyAffected, indirectlyAffected);
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/{endpointId}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> linkEndpointToEventType(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId, @PathParam("eventTypeId") UUID eventTypeId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.linkEndpoint(principal.getAccount(), endpointId, eventTypeId)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}/{endpointId}")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "204", description = "No Content", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> unlinkEndpointFromEventType(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId, @PathParam("eventTypeId") UUID eventTypeId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.unlinkEndpoint(principal.getAccount(), endpointId, eventTypeId)
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @GET
    @Path("/eventTypes/{eventTypeId}")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Multi<Endpoint> getLinkedEndpoints(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @BeanParam Query query) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getLinkedEndpoints(principal.getAccount(), eventTypeId, query);
    }

    @GET
    @Path("/defaults")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    @Operation(summary = "Retrieve all integrations of the configured default actions.")
    public Multi<Endpoint> getEndpointsForDefaults(@Context SecurityContext sec) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.getDefaultEndpoints(principal.getAccount());
    }

    @PUT
    @Path("/defaults/{endpointId}")
    @Operation(summary = "Add an integration to the list of configured default actions.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Uni<Response> addEndpointToDefaults(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.addEndpointToDefaults(principal.getAccount(), endpointId)
                .onItem().transform(ignored -> Response.ok().build());
    }

    @DELETE
    @Path("/defaults/{endpointId}")
    @Operation(summary = "Remove an integration from the list of configured default actions.")
    @APIResponse(responseCode = "204", description = "No Content", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Uni<Response> deleteEndpointFromDefaults(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return resources.deleteEndpointFromDefaults(principal.getAccount(), endpointId)
                .onItem().transform(ignored -> Response.noContent().build());
    }

    @GET
    @Path("/facets/applications")
    @Operation(summary = "Return a thin list of configured applications. This can be used to configure a filter in the UI")
    public Multi<Facet> getApplicationsFacets(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {
        return apps.getApplications(bundleName).onItem().transform(a -> new Facet(a.getId().toString(), a.getName(), a.getDisplay_name()));
    }

    @GET
    @Path("/facets/bundles")
    @Operation(summary = "Return a thin list of configured bundles. This can be used to configure a filter in the UI")
    public Multi<Facet> getBundleFacets(@Context SecurityContext sec) {
        return bundleResources.getBundles().onItem().transform(b -> new Facet(b.getId().toString(), b.getName(), b.getDisplay_name()));
    }
}
