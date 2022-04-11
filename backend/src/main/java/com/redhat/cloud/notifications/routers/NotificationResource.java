package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Facet;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
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
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
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
public class NotificationResource {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointRepository endpointRepository;

    private String getAccountId(SecurityContext sec) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        return principal.getAccount();
    }

    @DELETE
    @Path("/{id}")
    @Produces(TEXT_PLAIN)
    @APIResponse(responseCode = "204", description = "Notification has been marked as read", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    public Response markRead(@Context SecurityContext sec, @PathParam("id") Integer id) {
        // Mark the notification id for <tenantId><userId> 's subscription as read
        return null;
    }

    @GET
    @Path("/eventTypes")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve all event types. The returned list can be filtered by bundle or application.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Page<EventType> getEventTypes(@Context UriInfo uriInfo, @BeanParam Query query, @QueryParam("applicationIds") Set<UUID> applicationIds, @QueryParam("bundleId") UUID bundleId) {
        List<EventType> eventTypes = applicationRepository.getEventTypes(query, applicationIds, bundleId);
        Long count = applicationRepository.getEventTypesCount(applicationIds, bundleId);
        return new Page<>(
                eventTypes,
                PageLinksBuilder.build(uriInfo.getPath(), count, query.getLimit().getLimit(), query.getLimit().getOffset()),
                new Meta(count)
        );
    }

    /*
     * Called by the UI to build the behavior group removal confirmation screen.
     * That screen shows all the event types (and their application) that will be affected by the behavior group removal.
     */
    @GET
    @Path("/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the event types affected by the removal of a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<EventType> getEventTypesAffectedByRemovalOfBehaviorGroup(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId) {
        String accountId = getAccountId(sec);
        return behaviorGroupRepository.findEventTypesByBehaviorGroupId(accountId, behaviorGroupId);
    }

    /*
     * Called by the UI to build the endpoint removal confirmation screen.
     * That screen shows all the behavior groups that will be affected by the endpoint removal.
     */
    @GET
    @Path("/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups affected by the removal of an endpoint.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> getBehaviorGroupsAffectedByRemovalOfEndpoint(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        String accountId = getAccountId(sec);
        return behaviorGroupRepository.findBehaviorGroupsByEndpointId(accountId, endpointId);
    }

    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups linked to an event type.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> getLinkedBehaviorGroups(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @BeanParam Query query) {
        String accountId = getAccountId(sec);
        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(accountId, eventTypeId, query);
    }

    @GET
    @Path("/facets/applications")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Return a thin list of configured applications. This can be used to configure a filter in the UI")
    public List<Facet> getApplicationsFacets(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {
        return applicationRepository.getApplications(bundleName)
                .stream()
                .map(a -> new Facet(a.getId().toString(), a.getName(), a.getDisplayName()))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/facets/bundles")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Return a thin list of configured bundles. This can be used to configure a filter in the UI")
    public List<Facet> getBundleFacets(@Context SecurityContext sec, @QueryParam("includeApplications") boolean includeApplications) {
        return bundleRepository.getBundles()
                .stream()
                .map(b -> {
                    List<Facet> applications = null;
                    if (includeApplications) {
                        applications = applicationRepository.getApplications(b.getId()).stream()
                                .map(a -> new Facet(a.getId().toString(), a.getName(), a.getDisplayName()))
                                .collect(Collectors.toList());
                    }

                    return new Facet(b.getId().toString(), b.getName(), b.getDisplayName(), applications);
                })
                .collect(Collectors.toList());
    }

    @POST
    @Path("/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public BehaviorGroup createBehaviorGroup(@Context SecurityContext sec, @NotNull @Valid BehaviorGroup behaviorGroup) {
        String accountId = getAccountId(sec);
        return behaviorGroupRepository.create(accountId, behaviorGroup);
    }

    @PUT
    @Path("/behaviorGroups/{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Update a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public Boolean updateBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID id, @NotNull @Valid BehaviorGroup behaviorGroup) {
        String accountId = getAccountId(sec);
        behaviorGroup.setId(id);
        return behaviorGroupRepository.update(accountId, behaviorGroup);
    }

    @DELETE
    @Path("/behaviorGroups/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Delete a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public Boolean deleteBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID behaviorGroupId) {
        String accountId = getAccountId(sec);
        return behaviorGroupRepository.delete(accountId, behaviorGroupId, false);
    }

    @PUT
    @Path("/behaviorGroups/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of actions of a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateBehaviorGroupActions(@Context SecurityContext sec, @PathParam("behaviorGroupId") UUID behaviorGroupId, List<UUID> endpointIds) {
        if (endpointIds == null) {
            throw new BadRequestException("The request body must contain an endpoints identifiers list");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (endpointIds.contains(null)) {
            throw new BadRequestException("The endpoints identifiers list should not contain empty values");
        }
        if (endpointIds.size() != endpointIds.stream().distinct().count()) {
            throw new BadRequestException("The endpoints identifiers list should not contain duplicates");
        }
        String accountId = getAccountId(sec);
        Status status = behaviorGroupRepository.updateBehaviorGroupActions(accountId, behaviorGroupId, endpointIds);
        return Response.status(status).build();
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of behavior groups of an event type.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    public Response updateEventTypeBehaviors(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (behaviorGroupIds.contains(null)) {
            throw new BadRequestException("The behavior groups identifiers list should not contain empty values");
        }
        String accountId = getAccountId(sec);
        boolean updated = behaviorGroupRepository.updateEventTypeBehaviors(accountId, eventTypeId, behaviorGroupIds);
        if (updated) {
            return Response.ok().build();
        } else {
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/bundles/{bundleId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups of a bundle.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> findBehaviorGroupsByBundleId(@Context SecurityContext sec, @PathParam("bundleId") UUID bundleId) {
        String accountId = getAccountId(sec);
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(accountId, bundleId);
        endpointRepository.loadProperties(
                behaviorGroups
                        .stream()
                        .map(BehaviorGroup::getActions)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .map(BehaviorGroupAction::getEndpoint)
                        .collect(Collectors.toList())
        );
        return behaviorGroups;
    }
}
