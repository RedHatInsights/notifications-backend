package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.dto.v3.notification.ApplicationDTO;
import com.redhat.cloud.notifications.models.dto.v3.notification.BundleDTO;
import com.redhat.cloud.notifications.models.dto.v3.notification.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v3.notification.NotificationMapper;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_3_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

public class NotificationResourceV3 extends NotificationResourceCommon {

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    NotificationMapper notificationMapper;

    @Path(API_NOTIFICATIONS_V_3_0 + "/notifications")
    public static class V3 extends NotificationResourceV3 {
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
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "notification")
    public Page<EventTypeDTO> getEventTypesV3(
            @Context SecurityContext securityContext, @Context UriInfo uriInfo, @BeanParam @Valid Query query, @QueryParam("applicationIds") Set<UUID> applicationIds,
            @QueryParam("bundleId") UUID bundleId, @QueryParam("eventTypeName") String eventTypeName, @QueryParam("excludeMutedTypes") boolean excludeMutedTypes
    ) {
        Page<EventType> entityPage = super.getEventTypes(securityContext, uriInfo, query, applicationIds, bundleId, eventTypeName, excludeMutedTypes);
        List<EventTypeDTO> eventTypeDTO = entityPage.getData().stream().map(notificationMapper::eventTypeToDTO).toList();
        return new Page<>(eventTypeDTO, entityPage.getLinks(), entityPage.getMeta());
    }

    @GET
    @Path("/bundles/{bundleName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a bundle by name", description = "Retrieves the details of a bundle by searching by its name.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "notification")
    public BundleDTO getBundleDTOByName(@Context final SecurityContext securityContext, @PathParam("bundleName") String bundleName) {
        return notificationMapper.bundleToDTO(super.getBundleByName(bundleName));
    }

    @GET
    @Path("/bundles/{bundleName}/applications/{applicationName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an application by bundle and application names", description = "Retrieves an application by bundle and application names. Use this endpoint to  find an application by searching for the bundle that the application is part of. This is useful if you do not know the UUID of the bundle or application.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "notification")
    public ApplicationDTO getApplicationByNameAndBundleName(
            @Context SecurityContext securityContext,
            @PathParam("bundleName") String bundleName,
            @PathParam("applicationName") String applicationName
    ) {
        return notificationMapper.applicationToDTO(super.getApplicationByNameAndBundleName(bundleName, applicationName));
    }

    @GET
    @Path("/bundles/{bundleName}/applications/{applicationName}/eventTypes/{eventTypeName}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an event type by bundle, application and event type names", description = "Retrieves the details of an event type by specifying the bundle name, the application name, and the event type name.")
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "notification")
    public EventTypeDTO getEventTypesByNameAndBundleAndApplicationName(
            @Context SecurityContext securityContext,
            @PathParam("bundleName") String bundleName,
            @PathParam("applicationName") String applicationName,
            @PathParam("eventTypeName") String eventTypeName
    ) {
        return notificationMapper.eventTypeToDTO(super.getEventTypesByNameAndBundleAndApplicationName(bundleName, applicationName, eventTypeName));
    }

    @GET
    @Path("/applications")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured applications", description = "Returns a list of configured applications that includes the application name, the display name, and the ID. You can use this list to configure a filter in the UI.")
    public List<ApplicationDTO> getApplications(@Context SecurityContext sec, @QueryParam("bundleName") String bundleName) {
        return applicationRepository.getApplications(bundleName)
                .stream()
                .map(notificationMapper::applicationToDTO)
                .toList();
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured bundles", description = "Returns a list of configured bundles that includes the bundle name, the display name, and the ID. You can use this list to configure a filter in the UI.")
    public List<BundleDTO> getBundles(@Context SecurityContext sec, @QueryParam("includeApplications") boolean includeApplications) {
        if (includeApplications) {
            return bundleRepository.getBundlesWithApplications()
                    .stream()
                    .map(b -> {
                        BundleDTO bundleDTO = notificationMapper.bundleToDTO(b);
                        bundleDTO.setApplications(
                                b.getApplications().stream()
                                        .map(notificationMapper::applicationToDTO)
                                        .toList()
                        );
                        return bundleDTO;
                    })
                    .toList();
        }
        return bundleRepository.getBundles()
                .stream()
                .map(notificationMapper::bundleToDTO)
                .toList();
    }

    @GET
    @Path("/severities")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List configured severities", description = "Returns the list of available notification severities")
    public Set<Severity> getSeverities(@Context final SecurityContext sec) {
        return EnumSet.allOf(Severity.class);
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
    @Authorization(legacyRBACRole = RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "notification")
    public Page<EndpointDTO> getLinkedEndpoints(@Context final SecurityContext sec, @RestPath("eventTypeId") final UUID eventTypeId, @BeanParam @Valid final Query query, @Context final UriInfo uriInfo) {
        String orgId = getOrgId(sec);

        final List<Endpoint> endpoints = endpointEventTypeRepository.findEndpointsByEventTypeId(orgId, eventTypeId, query);
        Long count = endpointEventTypeRepository.countEndpointsByEventTypeId(orgId, eventTypeId);

        List<EndpointDTO> endpointDTOS = endpoints.stream().map(endpoint -> endpointMapper.toDTO(endpoint)).toList();
        return new Page<>(
                endpointDTOS,
                PageLinksBuilder.build(uriInfo, count, query),
                new Meta(count)
        );
    }

    @PUT
    @Path("/eventTypes/{eventTypeId}/endpoints")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Update the list of endpoints for an event type", description = "Updates the list of endpoints associated with an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "400", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)),
                description = "The request body is invalid or contains null values."),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN, schema = @Schema(type = SchemaType.STRING)),
                description = "The event type was not found.")
    })
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT, resourceType = "behavior_group")
    public Response updateEventTypeEndpoints(@Context SecurityContext securityContext,
                                             @Parameter(description = "UUID of the eventType to associate with the endpoint(s)") @PathParam("eventTypeId") UUID eventTypeId,
                                             @Parameter(description = "Set of endpoint ids to associate") Set<UUID> endpointsIds) {
        return super.updateEventTypeEndpoints(securityContext, eventTypeId, endpointsIds);
    }
}
