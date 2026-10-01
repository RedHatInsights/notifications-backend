package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.CommonMapper;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.oapi.OApiFilter;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import com.redhat.cloud.notifications.security.SecurityLog;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

// Email endpoints are not added at this point
// TODO Needs documentation annotations
public class EndpointResource extends EndpointResourceCommon {

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    CommonMapper commonMapper;

    /**
     * Used to create the secrets in Sources and update the endpoint's properties' IDs.
     */
    @Inject
    SecretUtils secretUtils;

    @Path(API_INTEGRATIONS_V_1_0 + "/endpoints")
    static class V1 extends EndpointResource {
    }

    @GET
    @Path("/{id}/history")
    @Produces(APPLICATION_JSON)
    @Parameters({
        @Parameter(
            name = "limit",
            in = ParameterIn.QUERY,
            description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used.",
            schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
            ),
        @Parameter(
            name = "pageNumber",
            in = ParameterIn.QUERY,
            description = "Page number. Starts at first page (0), if not specified starts at first page.",
            schema = @Schema(type = SchemaType.INTEGER)
            ),
        @Parameter(
            name = "includeDetail",
            description = "Include the detail in the reply",
            schema = @Schema(type = SchemaType.BOOLEAN)
            )
    })
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public List<NotificationHistoryDTO> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @Valid @BeanParam Query query) {
        if (!this.endpointRepository.existsByUuidAndOrgId(id, getOrgId(sec))) {
            throw new NotFoundException("Endpoint not found");
        }

        // TODO We need globally limitations (Paging support and limits etc)
        String orgId = getOrgId(sec);
        boolean doDetail = includeDetail != null && includeDetail;
        return commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationRepository.getNotificationHistory(orgId, id, doDetail, query));
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List endpoints", description = "Provides a list of endpoints. Use this endpoint to find specific endpoints.")
    @Parameters({
        @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used",
                schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
            ),
        @Parameter(
                name = "pageNumber",
                in = ParameterIn.QUERY,
                description = "Page number. Starts at first page (0), if not specified starts at first page.",
                schema = @Schema(type = SchemaType.INTEGER)
            )
    })
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointPage getEndpoints(
        @Context                SecurityContext sec,
        @BeanParam @Valid       Query query,
        @QueryParam("type")     List<String> targetType,
        @QueryParam("active")   Boolean activeOnly,
        @QueryParam("name")     String name
    ) {
        return internalGetEndpoints(sec, query, targetType, activeOnly, name, false, false);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a new endpoint", description = "Creates a new endpoint by providing data such as a description, a name, and the endpoint properties. Use this endpoint to create endpoints for integration with third-party services such as webhooks, Slack, or Google Chat.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = EndpointDTO.class))),
        @APIResponse(responseCode = "400", description = "Bad data passed, that does not correspond to the definition or Endpoint.properties are empty")
    })
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public EndpointDTO createEndpoint(
        @Context                        final SecurityContext sec,
        @NotNull @Valid @RequestBody    final EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = this.endpointMapper.toEntity(endpointDTO);

        try {
            return endpointMapper.toDTO(internalCreateEndpoint(sec, endpoint, endpointDTO.eventTypes));
        } catch (final Exception e) {
            SecurityLog.logCrudFailure("CREATE", "integration", "N/A", sec, e.getMessage());
            // Clean up the secrets from Sources if any were created.
            this.secretUtils.deleteSecretsForEndpoint(endpoint);

            throw e;
        }
    }

    @POST
    @Path("/system/email_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create an email subscription endpoint", description = "Adds the email subscription endpoint into the system and specifies the role-based access control (RBAC) group that will receive email notifications. Use this endpoint in behavior groups to send emails when an action linked to the behavior group is triggered.")
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public EndpointDTO getOrCreateEmailSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(getOrCreateSystemSubscriptionEndpoint(sec, requestProps, EMAIL_SUBSCRIPTION));
    }

    @POST
    @Path("/system/drawer_subscription")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add a drawer endpoint", description = "Adds the drawer system endpoint into the system and specifies the role-based access control (RBAC) group that will receive notifications. Use this endpoint to add an animation as a notification in the UI.")
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public EndpointDTO getOrCreateDrawerSubscriptionEndpoint(@Context SecurityContext sec, @NotNull @Valid @RequestBody(required = true) RequestSystemSubscriptionProperties requestProps) {
        return this.endpointMapper.toDTO(this.getOrCreateSystemSubscriptionEndpoint(sec, requestProps, DRAWER));
    }

    protected Endpoint getOrCreateSystemSubscriptionEndpoint(SecurityContext sec, RequestSystemSubscriptionProperties requestProps, EndpointType endpointType) {
        RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        getOrCreateInternalEndpointCommonChecks(requestProps, principal);

        // Prevent from creating not public facing properties
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setOnlyAdmins(requestProps.isOnlyAdmins());
        properties.setGroupId(requestProps.getGroupId());
        if (null != requestProps.getGroupId()) {
            properties.setGroupIds(Set.of(requestProps.getGroupId()));
        }
        return endpointRepository.getOrCreateSystemSubscriptionEndpoint(accountId, orgId, properties, endpointType);
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return internalGetEndpoint(sec, id, false, false);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete an endpoint", description = "Deletes an endpoint. Use this endpoint to delete an endpoint that is no longer needed. Deleting an endpoint that is already linked to a behavior group will unlink it from the behavior group. You cannot delete system endpoints.")
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.deleteEndpoint(sec, id);
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Enable an endpoint", description = "Enables an endpoint that is disabled so that the endpoint will be executed on the following operations that use the endpoint. An operation must be restarted to use the enabled endpoint.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.enableEndpoint(sec, id);
    }

    @DELETE
    @Path("/{id}/enable")
    @Operation(summary = "Disable an endpoint", description = "Disables an endpoint so that the endpoint will not be executed after an operation that uses the endpoint is started. An operation that is already running can still execute the endpoint. Disable an endpoint when you want to stop it from running and might want to re-enable it in the future.")
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.disableEndpoint(sec, id);
    }

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Update an endpoint", description = "Updates the endpoint configuration. Use this to update an existing endpoint. Any changes to the endpoint take place immediately.")
    @Path("/{id}")
    @Produces(TEXT_PLAIN)
    @PUT
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public Response updateEndpoint(
        @Context                                        SecurityContext securityContext,
        @PathParam("id")                                UUID id,
        @RequestBody(required = true) @NotNull @Valid   EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = endpointMapper.toEntity(endpointDTO);
        return super.commonUpdateEndpoint(securityContext, id, endpoint, endpointDTO.eventTypes, true);
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve event notification details", description = "Retrieves extended information about the outcome of an event notification related to the specified endpoint. Use this endpoint to learn why an event delivery failed.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public Response getDetailedEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID endpointId, @PathParam("history_id") UUID historyId) {
        return super.getDetailedEndpointHistory(sec, endpointId, historyId);
    }

    /**
     * Sends an integration test event via the specified endpoint.
     * @param uuid the {@link UUID} of the endpoint to test.
     * @return a "no content" response on success.
     */
    @APIResponse(responseCode = "204", description = "No Content")
    @Consumes(APPLICATION_JSON)
    @POST
    @Path("/{uuid}/test")
    @Operation(summary = "Generate a test notification", description = "Generates a test notification for a particular endpoint. Use this endpoint to test that an integration that you created works as expected. This endpoint triggers a test notification that should be received by the target recipient. For example, if you set up a webhook as the action to take upon receiving a notification, you should receive a test notification when using this endpoint.")
    @Parameters({
        @Parameter(
                name = "uuid",
                in = ParameterIn.PATH,
                description = "The UUID of the endpoint to test",
                schema = @Schema(type = SchemaType.STRING)
            )
    })
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public void testEndpoint(@Context SecurityContext sec, @RestPath UUID uuid, @Valid @RequestBody final EndpointTestRequest requestBody) {
        super.commonTestEndpoint(sec, uuid, requestBody);
    }

    @DELETE
    @Path("/{endpointId}/eventType/{eventTypeId}")
    @Operation(summary = "Delete the link between an endpoint and an event type", description = "Delete the link between an endpoint and an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with the passed id.")
    })
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = INTEGRATIONS_EDIT)
    public void deleteEventTypeFromEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @RestPath final UUID endpointId) {
        final String orgId = getOrgId(securityContext);
        endpointEventTypeRepository.deleteEndpointFromEventType(eventTypeId, endpointId, orgId);

        // Sync behavior group model
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEndpointId(orgId, endpointId);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            if (behaviorGroup.getBehaviors().stream().anyMatch(bg -> bg.getId().eventTypeId.equals(eventTypeId))) {
                if (behaviorGroup.getActions().size() == 1) {
                    behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
                } else {
                    behaviorGroupRepository.deleteEndpointFromBehaviorGroup(behaviorGroup.getId(), endpointId, orgId);
                }
            }
        }
    }

    @PUT
    @Path("/{endpointId}/eventType/{eventTypeId}")
    @Operation(summary = "Add a link between an endpoint and an event type", description = "Add a link between an endpoint and an event type.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with the passed id.")
    })
    @Tag(name = OApiFilter.PRIVATE)
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = INTEGRATIONS_EDIT)
    public void addEventTypeToEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID eventTypeId, @RestPath final UUID endpointId) {
        final String orgId = getOrgId(securityContext);
        final String accountId = getAccountId(securityContext);

        Endpoint updatedEndpoint = endpointEventTypeRepository.addEventTypeToEndpoint(eventTypeId, endpointId, orgId);

        // Sync behavior group model
        createOrUpdateLinkedBehaviorGroup(Set.of(eventTypeId), endpointId, updatedEndpoint.getName(), orgId, accountId);
    }

    @PUT
    @Path("/{endpointId}/eventTypes")
    @Operation(summary = "Update  links between an endpoint and event types", description = "Update  links between an endpoint and event types.")
    @APIResponses(value = {
        @APIResponse(responseCode = "204", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(type = SchemaType.STRING))),
        @APIResponse(responseCode = "404", content = @Content(mediaType = TEXT_PLAIN,  schema = @Schema(type = SchemaType.STRING)),
            description = "No event type or endpoint found with passed ids.")
    })
    @Transactional
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = INTEGRATIONS_EDIT)
    public void updateEventTypesLinkedToEndpoint(@Context final SecurityContext securityContext, @RestPath final UUID endpointId, @Parameter(description = "Set of event type ids to associate") Set<UUID> eventTypeIds) {
        internalUpdateEventTypesLinkedToEndpoint(securityContext, endpointId, eventTypeIds);
    }
}
