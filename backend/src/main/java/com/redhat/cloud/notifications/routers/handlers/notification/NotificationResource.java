package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.models.Facet;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupRequest;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.CreateBehaviorGroupResponse;
import com.redhat.cloud.notifications.routers.models.behaviorgroup.UpdateBehaviorGroupRequest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

public class NotificationResource {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    BackendConfig backendConfig;

    @Path(API_NOTIFICATIONS_V_1_0 + "/notifications")
    public static class V1 extends NotificationResource {
    }

    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List the behavior groups linked to an event type", description = "Lists the behavior groups that are linked to an event type. Use this endpoint to see which behavior groups will be affected if you delete an event type.")
    @Parameter(
        name = "limit",
        in = ParameterIn.QUERY,
        description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used.",
        schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
    )
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public List<BehaviorGroup> getLinkedBehaviorGroups(
        @Context SecurityContext sec,
        @PathParam("eventTypeId") UUID eventTypeId,
        @BeanParam @Valid Query query
    ) {
        final String orgId = getOrgId(sec);

        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(orgId, eventTypeId, query);
    }

    @GET
    @Path("/eventTypes")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List all event types", description = "Lists all event types. You can filter the returned list by bundle, application name, or unmuted types.")
    @Parameter(
        name = "limit",
        in = ParameterIn.QUERY,
        description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used.",
        schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
    )
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public Page<EventType> getEventTypes(
        @Context SecurityContext securityContext, @Context UriInfo uriInfo, @BeanParam @Valid Query query, @QueryParam("applicationIds") Set<UUID> applicationIds,
        @QueryParam("bundleId") UUID bundleId, @QueryParam("eventTypeName") String eventTypeName, @QueryParam("excludeMutedTypes") boolean excludeMutedTypes
    ) {
        List<UUID> unmutedEventTypeIds = excludeMutedTypes
            ? behaviorGroupRepository.findUnmutedEventTypes(getOrgId(securityContext), bundleId)
            : null;

        final String orgId = getOrgId(securityContext);
        final boolean showHiddenEventTypes = backendConfig.isShowHiddenEventTypes(orgId);
        List<EventType> eventTypes = applicationRepository.getEventTypes(query, applicationIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds, showHiddenEventTypes);
        Long count = applicationRepository.getEventTypesCount(applicationIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds, showHiddenEventTypes);
        return new Page<>(
            eventTypes,
            PageLinksBuilder.build(uriInfo, count, query.getLimit().getLimit(), query.getLimit().getOffset()),
            new Meta(count)
        );
    }

    @GET
    @Path("/bundles/{bundleName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a bundle by name", description = "Retrieves the details of a bundle by searching by its name.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public Bundle getBundleByName(@Context final SecurityContext securityContext, @PathParam("bundleName") String bundleName) {
        Bundle bundle = bundleRepository.getBundle(bundleName);
        if (bundle == null) {
            throw new NotFoundException();
        }

        return bundle;
    }

    @GET
    @Path("/bundles/{bundleName}/applications/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an application by bundle and application names", description = "Retrieves an application by bundle and application names. Use this endpoint to  find an application by searching for the bundle that the application is part of. This is useful if you do not know the UUID of the bundle or application.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public Application getApplicationByNameAndBundleName(
        @Context SecurityContext securityContext,
        @PathParam("bundleName") String bundleName,
        @PathParam("applicationName") String applicationName
    ) {
        Application application = applicationRepository.getApplication(bundleName, applicationName);
        if (application == null) {
            throw new NotFoundException();
        }

        return application;
    }

    @GET
    @Path("/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an event type by bundle, application and event type names", description = "Retrieves the details of an event type by specifying the bundle name, the application name, and the event type name.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public EventType getEventTypesByNameAndBundleAndApplicationName(
        @Context SecurityContext securityContext,
        @PathParam("bundleName") String bundleName,
        @PathParam("applicationName") String applicationName,
        @PathParam("eventTypeName") String eventTypeName
    ) {
        EventType eventType = applicationRepository.getEventType(bundleName, applicationName, eventTypeName);
        if (eventType == null) {
            throw new NotFoundException();
        }

        return eventType;
    }


    /*
     * Called by the UI to build the behavior group removal confirmation screen.
     * That screen shows all the event types (and their application) that will be affected by the behavior group removal.
     */
    @GET
    @Path("/eventTypes/affectedByRemovalOfBehaviorGroup/{behaviorGroupId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List the event types affected by the removal of a behavior group", description = "Lists the event types that will be affected by the removal of a behavior group. Use this endpoint to see which event types will be removed if you delete a behavior group.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public List<EventType> getEventTypesAffectedByRemovalOfBehaviorGroup(@Context SecurityContext sec,
                                                                         @Parameter(description = "The UUID of the behavior group to check") @PathParam("behaviorGroupId") UUID behaviorGroupId) {
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.findEventTypesByBehaviorGroupId(orgId, behaviorGroupId);
    }

    /*
     * Called by the UI to build the endpoint removal confirmation screen.
     * That screen shows all the behavior groups that will be affected by the endpoint removal.
     */
    @GET
    @Path("/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List the behavior groups affected by the removal of an endpoint", description = "Lists the behavior groups that are affected by the removal of an endpoint. Use this endpoint to understand how removing an endpoint affects existing behavior groups.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public List<BehaviorGroup> getBehaviorGroupsAffectedByRemovalOfEndpoint(@Context SecurityContext sec, @PathParam("endpointId") UUID endpointId) {
        String orgId = getOrgId(sec);
        return behaviorGroupRepository.findBehaviorGroupsByEndpointId(orgId, endpointId);
    }

    @GET
    @Path("/facets/applications")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured applications", description = "Returns a list of configured applications that includes the application name, the display name, and the ID. You can use this list to configure a filter in the UI.")
    public List<Facet> getApplicationsFacets(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {
        return applicationRepository.getApplications(bundleName)
            .stream()
            .map(a -> new Facet(a.getId().toString(), a.getName(), a.getDisplayName()))
            .collect(Collectors.toList());
    }

    @GET
    @Path("/facets/bundles")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured bundles", description = "Returns a list of configured bundles that includes the bundle name, the display name, and the ID. You can use this list to configure a filter in the UI.")
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

    @GET
    @Path("/severities")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured severities", description = "Returns the list of available notification severities")
    public Set<Severity> getSeverities(@Context final SecurityContext sec) {
        return EnumSet.allOf(Severity.class);
    }

    /**
     * Creates a new behavior group. The payload requires to either specify the
     * related bundle ID, or its name. This is due to a feature request coming
     * from the frontend team, which was forced to fetch all bundles in order
     * to grab their UUID, when apparently they could simply send the bundle
     * name instead. More information in the related Jira ticket
     * <a href="https://issues.redhat.com/browse/RHCLOUD-22513">RHCLOUD-22513</a>.
     * @param sec the security context needed to get the account ID and the Org ID.
     * @param request the incoming valid {@link CreateBehaviorGroupRequest}.
     * @return the created behavior group to the client.
     */
    @POST
    @Path("/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Create a behavior group", description = "Creates a behavior group that defines which notifications will be sent to external services after an event is received. Use this endpoint to control the types of events users are notified about.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = CreateBehaviorGroupResponse.class))),
        @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)), description = "Bad or no content passed.")
    })
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public CreateBehaviorGroupResponse createBehaviorGroup(
        @Context final SecurityContext sec,
        @NotNull @Valid @RequestBody(required = true) final CreateBehaviorGroupRequest request
    ) {
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        // We know that either the ID or the name are present because the
        // request gets validated before reaching this point, and therefore it
        // is safe to assume that either the bundle ID or the bundle name will
        // be present.
        UUID bundleId = request.bundleId;
        if (bundleId == null) {
            final Optional<Bundle> bundle = this.bundleRepository.findByName(request.bundleName);
            if (bundle.isEmpty()) {
                throw new NotFoundException("the specified bundle was not found in the database");
            }

            bundleId = bundle.get().getId();
        }

        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setBundleId(bundleId);
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
        response.bundleId = behaviorGroup.getBundleId();
        response.displayName = behaviorGroup.getDisplayName();

        response.endpoints = behaviorGroup.getActions().stream().map(action -> action.getId().endpointId).collect(Collectors.toList());
        response.eventTypes = behaviorGroup.getBehaviors().stream().map(b -> b.getId().eventTypeId).collect(Collectors.toSet());

        response.created = behaviorGroup.getCreated();

        endpointEventTypeRepository.refreshEndpointLinksToEventTypeFromBehaviorGroup(orgId, Set.of(behaviorGroup.getId()));
        return response;
    }

    @PUT
    @Path("/behaviorGroups/{id}")
    @Consumes(APPLICATION_JSON)
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.BOOLEAN))),
        @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)),
            description = "Bad or no content passed."),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No behavior group found with the passed id.")
    })
    @Operation(summary = "Update a behavior group", description = "Updates the details of a behavior group. Use this endpoint to update the list of related endpoints and event types associated with this behavior group.")
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public Response updateBehaviorGroup(@Context SecurityContext sec,
                                        @Parameter(description = "The UUID of the behavior group to update") @PathParam("id") UUID id,
                                        @NotNull @RequestBody(description = "New parameters", required = true) @Valid UpdateBehaviorGroupRequest request) {
        String orgId = getOrgId(sec);

        if (request.displayName != null) {
            UUID bundleId = behaviorGroupRepository.getBundleId(orgId, id);
            BehaviorGroup behaviorGroup = new BehaviorGroup();
            behaviorGroup.setId(id);
            behaviorGroup.setDisplayName(request.displayName);
            behaviorGroup.setBundleId(bundleId);

            behaviorGroupRepository.update(orgId, behaviorGroup);
        }

        List<UUID> endpointLinkedToBgBeforeUpdate = new ArrayList<>();
        if (request.endpointIds != null) {
            endpointLinkedToBgBeforeUpdate = endpointEventTypeRepository.findEndpointsByBehaviorGroupId(orgId, Set.of(id));
            behaviorGroupRepository.updateBehaviorGroupActions(orgId, id, request.endpointIds);
        }

        if (request.eventTypeIds != null) {
            behaviorGroupRepository.updateBehaviorEventTypes(orgId, id, request.eventTypeIds);
        }

        final List<UUID> endpointLinkedToBgAfterUpdate = endpointEventTypeRepository.findEndpointsByBehaviorGroupId(orgId, Set.of(id));
        endpointEventTypeRepository.refreshEndpointLinksToEventType(orgId, Stream.concat(endpointLinkedToBgBeforeUpdate.stream(), endpointLinkedToBgAfterUpdate.stream()).toList());
        return Response.status(200).type(APPLICATION_JSON).entity(true).build();
    }

    @DELETE
    @Path("/behaviorGroups/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Delete a behavior group", description = "Deletes a behavior group and all of its configured actions. Use this endpoint when you no longer need a behavior group.")
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public Boolean deleteBehaviorGroup(@Context SecurityContext sec,
                                       @Parameter(description = "The UUID of the behavior group to delete") @PathParam("id") UUID behaviorGroupId) {
        String orgId = getOrgId(sec);
        final List<UUID> endpointsLinkedToBgToDelete = endpointEventTypeRepository.findEndpointsByBehaviorGroupId(orgId, Set.of(behaviorGroupId));
        final Boolean response = behaviorGroupRepository.delete(orgId, behaviorGroupId);
        endpointEventTypeRepository.refreshEndpointLinksToEventType(orgId, endpointsLinkedToBgToDelete);
        return response;
    }

    @PUT
    @Path("/behaviorGroups/{behaviorGroupId}/actions")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of behavior group actions", description = "Updates the list of actions to be executed in that particular behavior group after an event is received.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public Response updateBehaviorGroupActions(@Context SecurityContext sec,
                                               @Parameter(description = "The UUID of the behavior group to update") @PathParam("behaviorGroupId") UUID behaviorGroupId,
                                               @Parameter(description = "List of endpoint ids of the actions") List<UUID> endpointIds) {
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
        String orgId = getOrgId(sec);
        final List<UUID> endpointLinkedToBgBeforeUpdate = endpointEventTypeRepository.findEndpointsByBehaviorGroupId(orgId, Set.of(behaviorGroupId));
        behaviorGroupRepository.updateBehaviorGroupActions(orgId, behaviorGroupId, endpointIds);
        final List<UUID> endpointLinkedToBgAfterUpdate = endpointEventTypeRepository.findEndpointsByBehaviorGroupId(orgId, Set.of(behaviorGroupId));

        // Sync new endpoint to evenType data model
        endpointEventTypeRepository.refreshEndpointLinksToEventType(orgId, Stream.concat(endpointLinkedToBgBeforeUpdate.stream(), endpointLinkedToBgAfterUpdate.stream()).toList());
        return Response.ok().build();
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of behavior groups for an event type", description = "Updates the list of behavior groups associated with an event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public Response updateEventTypeBehaviors(@Context SecurityContext sec,
                                             @Parameter(description = "UUID of the eventType to associate with the behavior group(s)") @PathParam("eventTypeId") UUID eventTypeId,
                                             @Parameter(description = "Set of behavior group ids to associate") Set<UUID> behaviorGroupIds) {
        if (behaviorGroupIds == null) {
            throw new BadRequestException("The request body must contain a list (possibly empty) of behavior groups identifiers");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (behaviorGroupIds.contains(null)) {
            throw new BadRequestException("The behavior groups identifiers list should not contain empty values");
        }
        String orgId = getOrgId(sec);
        final Set<UUID> updatedBgs = behaviorGroupRepository.updateEventTypeBehaviors(orgId, eventTypeId, behaviorGroupIds);

        // Sync new endpoint to evenType data model
        endpointEventTypeRepository.refreshEndpointLinksToEventTypeFromBehaviorGroup(orgId, updatedBgs);
        return Response.ok().build();
    }

    /**
     * Appends the given behavior group to the specified event type.
     * @param securityContext the security context to get the org id from.
     * @param behaviorGroupUuid the UUID of the behavior group that, supposedly, they just created.
     * @param eventTypeUuid the UUID of the event type.
     */
    @PUT
    @Path("/eventTypes/{eventTypeUuid}/behaviorGroups/{behaviorGroupUuid}")
    @Operation(summary = "Add a behavior group to the given event type.")
    @APIResponse(responseCode = "204")
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public void appendBehaviorGroupToEventType(
        @Context final SecurityContext securityContext,
        @RestPath final UUID behaviorGroupUuid,
        @RestPath final UUID eventTypeUuid
    ) {
        final String orgId = getOrgId(securityContext);

        this.behaviorGroupRepository.appendBehaviorGroupToEventType(orgId, behaviorGroupUuid, eventTypeUuid);

        // Sync new endpoint to evenType data model
        endpointEventTypeRepository.refreshEndpointLinksToEventTypeFromBehaviorGroup(orgId, Set.of(behaviorGroupUuid));
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}/behaviorGroups/{behaviorGroupId}")
    @Operation(summary = "Delete a behavior group from an event type", description = "Delete a behavior group from the specified event type.")
    @APIResponse(responseCode = "204")
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public void deleteBehaviorGroupFromEventType(
        @Context final SecurityContext securityContext,
        @RestPath final UUID eventTypeId,
        @RestPath final UUID behaviorGroupId
    ) {
        final String orgId = getOrgId(securityContext);

        this.behaviorGroupRepository.deleteBehaviorGroupFromEventType(eventTypeId, behaviorGroupId, orgId);

        // Sync new endpoint to evenType data model
        endpointEventTypeRepository.refreshEndpointLinksToEventTypeFromBehaviorGroup(orgId, Set.of(behaviorGroupId));
    }

    @GET
    @Path("/bundles/{bundleId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List behavior groups in a bundle", description = "Lists the behavior groups associated with a bundle. Use this endpoint to see the behavior groups that are configured for a particular bundle for a particular tenant.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public List<BehaviorGroup> findBehaviorGroupsByBundleId(@Context SecurityContext sec,
                                                            @Parameter(description = "UUID of the bundle to retrieve the behavior groups for.") @PathParam("bundleId") UUID bundleId) {
        String orgId = getOrgId(sec);
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(orgId, bundleId);
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

    @GET
    @Path("/eventTypes/{eventTypeId}/endpoints")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the endpoints linked to an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type found with the passed id.")
    })
    @Tag(name = OApiFilter.PRIVATE)
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW)
    public Page<EndpointDTO> getLinkedEndpoints(@Context final SecurityContext sec, @RestPath("eventTypeId") final UUID eventTypeId, @BeanParam @Valid final Query query, @Context final UriInfo uriInfo) {
        String orgId = getOrgId(sec);

        final List<Endpoint> endpoints = endpointEventTypeRepository.findEndpointsByEventTypeId(orgId, eventTypeId, query);

        List<EndpointDTO> endpointDTOS = endpoints.stream().map(endpoint -> endpointMapper.toDTO(endpoint)).toList();
        return new Page<>(
            endpointDTOS,
            PageLinksBuilder.build(uriInfo, endpointDTOS.size(), query),
            new Meta(Long.valueOf(endpointDTOS.size()))
        );
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/endpoints")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of endpoints for an event type", description = "Updates the list of endpoints associated with an event type.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT)
    public Response updateEventTypeEndpoints(@Context SecurityContext securityContext,
                                             @Parameter(description = "UUID of the eventType to associate with the endpoint(s)") @PathParam("eventTypeId") UUID eventTypeId,
                                             @Parameter(description = "Set of endpoint ids to associate") Set<UUID> endpointsIds) {
        if (endpointsIds == null) {
            throw new BadRequestException("The request body must contain a list (possibly empty) of endpoints identifiers");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (endpointsIds.contains(null)) {
            throw new BadRequestException("The endpoints identifiers list should not contain empty values");
        }

        String orgId = getOrgId(securityContext);
        String accountId = getAccountId(securityContext);

        endpointEventTypeRepository.updateEventTypeEndpoints(orgId, eventTypeId, endpointsIds);

        // Sync behavior group model

        // delete endpoint from existing behavior group
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEventTypeId(orgId, eventTypeId, null);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            Set<UUID> associatedEventTypes = behaviorGroup.getBehaviors().stream().map(b -> b.getEventType().getId()).collect(Collectors.toSet());
            associatedEventTypes.remove(eventTypeId);
            if (associatedEventTypes.isEmpty()) {
                behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
            } else {
                behaviorGroupRepository.updateBehaviorEventTypes(orgId, behaviorGroup.getId(), associatedEventTypes);
            }
        }

        createOrUpdateLinkedBehaviorGroup(eventTypeId, endpointsIds, orgId, accountId);

        return Response.ok().build();
    }


    private void createOrUpdateLinkedBehaviorGroup(UUID eventTypeId, Set<UUID> endpointIds, String orgId, String accountId) {

        for (UUID endpointId : endpointIds) {
            EventType eventType = eventTypeRepository.findByIds(Set.of(eventTypeId)).getFirst();
            String behaviorGroupName = String.format("Event type \"%s\" behavior group", eventType.getName());

            Optional<Bundle> bundle = eventTypeRepository.findBundleByEventTypeId(eventTypeId);

            Optional<BehaviorGroup> existingBg = behaviorGroupRepository.findBehaviorGroupsByName(orgId, bundle.get().getId(), behaviorGroupName);
            if (existingBg.isPresent()) {
                Boolean alreadyAssociatedAction = existingBg.get().getActions().stream().anyMatch(bga -> bga.getId().endpointId.equals(endpointId));

                if (!alreadyAssociatedAction) {
                    int position = existingBg.get().getActions().stream().mapToInt(ba -> ba.getPosition()).max().orElse(-1) + 1;
                    behaviorGroupRepository.appendActionToBehaviorGroup(existingBg.get().getId(), endpointId, position, orgId);
                }

                Boolean alreadyAssociatedEventType = existingBg.get().getBehaviors().stream().anyMatch(bh -> bh.getId().eventTypeId.equals(eventTypeId));
                if (!alreadyAssociatedEventType) {
                    behaviorGroupRepository.appendBehaviorGroupToEventType(orgId, existingBg.get().getId(), eventTypeId);
                }
            } else {
                // Create or update legacy behavior group structure
                BehaviorGroup behaviorGroup = new BehaviorGroup();
                behaviorGroup.setBundleId(bundle.get().getId());
                behaviorGroup.setDisplayName(behaviorGroupName);

                behaviorGroupRepository.createFull(
                    accountId,
                    orgId,
                    behaviorGroup,
                    List.of(endpointId),
                    Set.of(eventTypeId)
                );
            }

        }
    }
}
