package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.rbac.RbacIdentityProvider;
import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BehaviorGroupResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications")
public class NotificationService {

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources apps;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Inject
    EndpointResources endpointResources;

    private Uni<String> getAccountId(SecurityContext sec) {
        return Uni.createFrom().item(() -> {
            RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
            return principal.getAccount();
        });
    }

    @DELETE
    @Path("/{id}")
    @Produces(TEXT_PLAIN)
    @APIResponse(responseCode = "204", description = "Notification has been marked as read", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> markRead(@Context SecurityContext sec, @PathParam("id") Integer id) {
        // Mark the notification id for <tenantId><userId> 's subscription as read
        return Uni.createFrom().nullItem();
    }

    @GET
    @Path("/eventTypes")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve all event types. The returned list can be filtered by bundle or application.")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Uni<List<EventType>> getEventTypes(@BeanParam Query query, @QueryParam("applicationIds") Set<UUID> applicationIds, @QueryParam("bundleId") UUID bundleId) {
        return apps.getEventTypes(query, applicationIds, bundleId);
    }

    /*
     * Called by the UI to build the behavior group removal confirmation screen.
     * That screen shows all the event types (and their application) that will be affected by the behavior group removal.
     */
    @GET
    @Path("/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the event types affected by the removal of a behavior group.")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Uni<List<EventType>> getEventTypesAffectedByRemovalOfBehaviorGroup(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.findEventTypesByBehaviorGroupId(accountId, behaviorGroupId));
    }

    /*
     * Called by the UI to build the endpoint removal confirmation screen.
     * That screen shows all the behavior groups that will be affected by the endpoint removal.
     */
    @GET
    @Path("/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups affected by the removal of an endpoint.")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Uni<List<BehaviorGroup>> getBehaviorGroupsAffectedByRemovalOfEndpoint(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.findBehaviorGroupsByEndpointId(accountId, endpointId));
    }

    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups linked to an event type.")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Uni<List<BehaviorGroup>> getLinkedBehaviorGroups(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @BeanParam Query query) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.findBehaviorGroupsByEventTypeId(accountId, eventTypeId, query));
    }

    @GET
    @Path("/facets/applications")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Return a thin list of configured applications. This can be used to configure a filter in the UI")
    public Uni<List<Facet>> getApplicationsFacets(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {
        return apps.getApplications(bundleName)
                .onItem().transform(apps -> apps.stream()
                        .map(a -> new Facet(a.getId().toString(), a.getName(), a.getDisplayName()))
                        .collect(Collectors.toList())
                );
    }

    @GET
    @Path("/facets/bundles")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Return a thin list of configured bundles. This can be used to configure a filter in the UI")
    public Uni<List<Facet>> getBundleFacets(@Context SecurityContext sec) {
        return bundleResources.getBundles()
                .onItem().transform(bundles -> bundles.stream()
                        .map(b -> new Facet(b.getId().toString(), b.getName(), b.getDisplayName()))
                        .collect(Collectors.toList())
                );
    }

    @POST
    @Path("/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a behavior group.")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Uni<BehaviorGroup> createBehaviorGroup(@Context SecurityContext sec, @NotNull @Valid BehaviorGroup behaviorGroup) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.create(accountId, behaviorGroup));
    }

    @PUT
    @Path("/behaviorGroups/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update a behavior group.")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Uni<Boolean> updateBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID id, @NotNull @Valid BehaviorGroup behaviorGroup) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> {
                    behaviorGroup.setId(id);
                    return behaviorGroupResources.update(accountId, behaviorGroup);
                });
    }

    @DELETE
    @Path("/behaviorGroups/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Delete a behavior group.")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Uni<Boolean> deleteBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID behaviorGroupId) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.delete(accountId, behaviorGroupId));
    }

    @PUT
    @Path("/behaviorGroups/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of actions of a behavior group.")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> updateBehaviorGroupActions(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId, List<UUID> endpointIds) {
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (endpointIds.contains(null)) {
            return Uni.createFrom().failure(new BadRequestException("The endpoints identifiers list should not contain empty values"));
        }
        if (endpointIds.size() != endpointIds.stream().distinct().count()) {
            return Uni.createFrom().failure(new BadRequestException("The endpoints identifiers list should not contain duplicates"));
        }
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.updateBehaviorGroupActions(accountId, behaviorGroupId, endpointIds))
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of behavior groups of an event type.")
    @RolesAllowed(RbacIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Uni<Response> updateEventTypeBehaviors(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (behaviorGroupIds.contains(null)) {
            return Uni.createFrom().failure(new BadRequestException("The behavior groups identifiers list should not contain empty values"));
        }
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.updateEventTypeBehaviors(accountId, eventTypeId, behaviorGroupIds))
                .onItem().transform(updated -> {
                    if (updated) {
                        return Response.ok().build();
                    } else {
                        return Response.status(Status.NOT_FOUND).build();
                    }
                });
    }

    @GET
    @Path("/bundles/{bundleId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups of a bundle.")
    @RolesAllowed(RbacIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Uni<List<BehaviorGroup>> findBehaviorGroupsByBundleId(@Context SecurityContext sec, @PathParam("bundleId") UUID bundleId) {
        return getAccountId(sec)
                .onItem().transformToUni(accountId -> behaviorGroupResources.findByBundleId(accountId, bundleId))
                .onItem().call(behaviorGroups -> endpointResources.loadProperties(
                        behaviorGroups
                                .stream()
                                .map(BehaviorGroup::getActions)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .map(BehaviorGroupAction::getEndpoint)
                                .collect(Collectors.toList())
                        )
                );
    }
}
