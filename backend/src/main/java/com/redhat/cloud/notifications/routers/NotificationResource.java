package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
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
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupResponse;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
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

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

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
    public Page<EventType> getEventTypes(
            @Context UriInfo uriInfo, @BeanParam Query query, @QueryParam("applicationIds") Set<UUID> applicationIds, @QueryParam("bundleId") UUID bundleId,
            @QueryParam("eventTypeName") String eventTypeName
    ) {
        List<EventType> eventTypes = applicationRepository.getEventTypes(query, applicationIds, bundleId, eventTypeName);
        Long count = applicationRepository.getEventTypesCount(applicationIds, bundleId, eventTypeName);
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
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.findEventTypesByBehaviorGroupId(accountId, orgId, behaviorGroupId);
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
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.findBehaviorGroupsByEndpointId(accountId, orgId, endpointId);
    }

    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups linked to an event type.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> getLinkedBehaviorGroups(@Context SecurityContext sec, @PathParam("eventTypeId") UUID eventTypeId, @BeanParam Query query) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(accountId, orgId, eventTypeId, query);
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
    @Operation(summary = "Create a behavior group - assigning actions and linking to event types as requested")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = BehaviorGroup.class))),
            @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)))
    })
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public CreateBehaviorGroupResponse createBehaviorGroup(@Context SecurityContext sec, CreateBehaviorGroupRequest request) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setBundleId(request.bundleId);
        behaviorGroup.setDisplayName(request.displayName);

        behaviorGroup = behaviorGroupRepository.createFull(
                accountId,
                orgId,
                behaviorGroup,
                request.endpointIds,
                request.eventTypeIds
        );

        CreateBehaviorGroupResponse response = new CreateBehaviorGroupResponse();

        response.id = behaviorGroup.getId();
        response.accountId = behaviorGroup.getAccountId();
        response.orgId = null;
        response.bundleId = behaviorGroup.getBundleId();
        response.displayName = behaviorGroup.getDisplayName();

        response.endpoints = behaviorGroup.getActions().stream().map(action -> action.getId().endpointId).collect(Collectors.toList());
        response.eventTypes = behaviorGroup.getBehaviors().stream().map(b -> b.getId().eventTypeId).collect(Collectors.toSet());

        response.created = behaviorGroup.getCreated();

        return response;
    }

    @PUT
    @Path("/behaviorGroups/{id}")
    @Consumes(APPLICATION_JSON)
    @APIResponses(value = {
            @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.BOOLEAN))),
            @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)))
    })
    @Operation(summary = "Update a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public Response updateBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID id, UpdateBehaviorGroupRequest request) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        if (request.displayName != null) {
            BehaviorGroup behaviorGroup = new BehaviorGroup();
            behaviorGroup.setId(id);
            behaviorGroup.setDisplayName(request.displayName);

            if (!behaviorGroupRepository.update(accountId, orgId, behaviorGroup)) {
                return Response.status(200).type(APPLICATION_JSON).entity(false).build();
            }
        }

        if (request.endpointIds != null) {
            behaviorGroupRepository.updateBehaviorGroupActions(accountId, orgId, id, request.endpointIds);
        }

        if (request.eventTypeIds != null) {
            behaviorGroupRepository.updateBehaviorEventTypes(accountId, orgId, id, request.eventTypeIds);
        }

        return Response.status(200).type(APPLICATION_JSON).entity(true).build();
    }

    @DELETE
    @Path("/behaviorGroups/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Delete a behavior group.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Transactional
    public Boolean deleteBehaviorGroup(@Context SecurityContext sec, @PathParam("id") UUID behaviorGroupId) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.delete(accountId, orgId, behaviorGroupId);
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
            throw new BadRequestException("The request body must contain a list (possibly empty) of endpoints identifiers");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (endpointIds.contains(null)) {
            throw new BadRequestException("The endpoints identifiers list should not contain empty values");
        }
        if (endpointIds.size() != endpointIds.stream().distinct().count()) {
            throw new BadRequestException("The endpoints identifiers list should not contain duplicates");
        }
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        behaviorGroupRepository.updateBehaviorGroupActions(accountId, orgId, behaviorGroupId, endpointIds);
        return Response.ok().build();
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
        if (behaviorGroupIds == null) {
            throw new BadRequestException("The request body must contain a list (possibly empty) of behavior groups identifiers");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (behaviorGroupIds.contains(null)) {
            throw new BadRequestException("The behavior groups identifiers list should not contain empty values");
        }
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        behaviorGroupRepository.updateEventTypeBehaviors(accountId, orgId, eventTypeId, behaviorGroupIds);
        return Response.ok().build();
    }

    @GET
    @Path("/bundles/{bundleId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the behavior groups of a bundle.")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> findBehaviorGroupsByBundleId(@Context SecurityContext sec, @PathParam("bundleId") UUID bundleId) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(accountId, orgId, bundleId);
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
