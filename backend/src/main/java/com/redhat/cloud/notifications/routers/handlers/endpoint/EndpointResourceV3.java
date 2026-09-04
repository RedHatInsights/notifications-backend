package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointMapperV3;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointPageDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointSecretsDTO;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import com.redhat.cloud.notifications.security.SecurityLog;
import io.quarkus.logging.Log;
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
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestPath;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTEGRATIONS_V_3_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

public class EndpointResourceV3 extends EndpointResourceCommon {

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    EndpointMapperV3 endpointMapperV3;

    @Path(API_INTEGRATIONS_V_3_0 + "/endpoints")
    public static class V3 extends EndpointResourceV3 {
    }

    @GET
    @Path("/{id}/history")
    @Produces(APPLICATION_JSON)
    @Parameters(
        {
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
        }
    )
    @Operation(operationId = "EndpointResource$V3_getEndpointHistory")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON)),
        @APIResponse(responseCode = "400", description = "Invalid query parameters"),
        @APIResponse(responseCode = "404", description = "Endpoint not found")
    })
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public Page<NotificationHistoryDTO> getEndpointHistory(
        @Context SecurityContext sec,
        @Context UriInfo uriInfo,
        @PathParam("id") UUID id,
        @QueryParam("includeDetail") Boolean includeDetail,
        @Valid @BeanParam Query query
    ) {
        if (!this.endpointRepository.existsByUuidAndOrgId(id, getOrgId(sec))) {
            throw new NotFoundException("Endpoint not found");
        }

        String orgId = getOrgId(sec);
        boolean doDetail = includeDetail != null && includeDetail;

        final List<NotificationHistory> notificationHistory = this.notificationRepository.getNotificationHistory(orgId, id, doDetail, query);
        final long notificationHistoryCount = this.notificationRepository.countNotificationHistoryElements(id, orgId);

        return new Page<>(
            commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationHistory),
            PageLinksBuilder.build(uriInfo, notificationHistoryCount, query.getLimit().getLimit(), query.getLimit().getOffset()),
            new Meta(notificationHistoryCount)
        );
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(operationId = "EndpointResource$V3_getEndpoint", summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        // Secrets are never part of the v3 payload (RHCLOUD-34316), so there is no need to fetch
        // them from Sources or redact them here.
        Endpoint endpoint = getEndpoint(sec, id, true, true, false);

        com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO endpointDTO = endpointMapperV3.toDTO(endpoint);

        endpointDTO.setEventTypesGroupByBundlesAndApplications(includeLinkedEventTypes(endpoint.getEventTypes()));

        endpointDTO.setReadOnly(endpoint.getOrgId() == null);

        return endpointDTO;
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(operationId = "EndpointResource$V3_getEndpoints", summary = "List endpoints", description = "Provides a list of endpoints. Use this endpoint to find specific endpoints.")
    @Parameters(
        {
            @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page. If the value is 0, it will return all elements",
                schema = @Schema(type = SchemaType.INTEGER)
            ),
            @Parameter(
                name = "pageNumber",
                in = ParameterIn.QUERY,
                description = "Page number. Starts at first page (0), if not specified starts at first page.",
                schema = @Schema(type = SchemaType.INTEGER)
            ),
            @Parameter(
                name = "type",
                in = ParameterIn.QUERY,
                description = "Filter by endpoint type (can be repeated for multiple types)",
                schema = @Schema(type = SchemaType.ARRAY)
            ),
            @Parameter(
                name = "active",
                in = ParameterIn.QUERY,
                description = "Filter by enabled/disabled status",
                schema = @Schema(type = SchemaType.BOOLEAN)
            ),
            @Parameter(
                name = "name",
                in = ParameterIn.QUERY,
                description = "Filter by endpoint name (partial match)",
                schema = @Schema(type = SchemaType.STRING)
            )
        }
    )
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = EndpointPageDTO.class))),
        @APIResponse(responseCode = "400", description = "Invalid query parameters")
    })
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointPageDTO getEndpoints(
        @Context                SecurityContext sec,
        @Context                UriInfo uriInfo,
        @BeanParam @Valid       Query query,
        @QueryParam("type")     List<String> targetType,
        @QueryParam("active")   Boolean activeOnly,
        @QueryParam("name")     String name
    ) {
        EndpointPageRecord foundEndpoints = getEndpoints(sec, query, targetType, activeOnly, name, true, true);

        final List<com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO> endpointDTOS = new ArrayList<>(foundEndpoints.endpoints().size());
        for (Endpoint endpoint: foundEndpoints.endpoints()) {
            // Secrets are never part of the v3 payload (RHCLOUD-34316), so there is no need to
            // fetch them from Sources or redact them here, unlike the v1/v2 equivalent.
            com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO endpointDTO = endpointMapperV3.toDTO(endpoint);
            endpointDTO.setEventTypesGroupByBundlesAndApplications(includeLinkedEventTypes(endpoint.getEventTypes()));
            endpointDTO.setReadOnly(endpoint.getOrgId() == null);
            endpointDTOS.add(endpointDTO);
        }

        return new EndpointPageDTO(
            endpointDTOS,
            PageLinksBuilder.build(uriInfo, foundEndpoints.count(), query.getLimit().getLimit(), query.getLimit().getOffset()),
            new Meta(foundEndpoints.count())
        );
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(operationId = "EndpointResource$V3_createEndpoint", summary = "Create a new endpoint", description = "Creates a new endpoint by providing data such as a description, a name, and the endpoint properties. Use this endpoint to create endpoints for integration with third-party services such as webhooks, Slack, or Google Chat. Secrets can optionally be supplied via the \"secrets\" field at creation time; they are never returned by the API. Once the endpoint exists, secrets can only be managed through the dedicated \"PUT/DELETE .../secrets\" endpoints.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = EndpointDTO.class))),
        @APIResponse(responseCode = "400", description = "Bad data passed, that does not correspond to the definition or Endpoint.properties are empty")
    })
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public EndpointDTO createEndpoint(
            @Context                     final SecurityContext sec,
            @NotNull @Valid @RequestBody final EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = endpointMapperV3.toEntity(endpointDTO);

        final EndpointSecretsDTO secrets = endpointDTO.getSecrets();
        if (secrets != null) {
            if (!(endpoint.getProperties() instanceof SourcesSecretable props)) {
                throw new BadRequestException("This endpoint type does not support secrets");
            }
            props.setSecretToken(secrets.getSecretToken());
            props.setBearerAuthentication(secrets.getBearerAuthentication());
        }

        try {
            return endpointMapperV3.toDTO(
                    internalCreateEndpoint(sec, endpoint, endpointDTO.getEventTypes())
            );
        } catch (final Exception e) {
            SecurityLog.logCrudFailure("CREATE", "integration", "N/A", sec, e.getMessage());

            try {
                // Clean up the secrets from Sources if any were created.
                secretUtils.deleteSecretsForEndpoint(endpoint);
            } catch (final Exception cleanupException) {
                Log.errorf(cleanupException, "Failed to clean up secrets after endpoint creation failure");
                e.addSuppressed(cleanupException);
            }

            throw e;
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "EndpointResource$V3_deleteEndpoint", summary = "Delete an endpoint", description = "Deletes an endpoint. Use this endpoint to delete an endpoint that is no longer needed. Deleting an endpoint that is already linked to a behavior group will unlink it from the behavior group. You cannot delete system endpoints.")
    @APIResponse(responseCode = "204", description = "The integration has been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response deleteEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.deleteEndpoint(sec, id);
    }

    @PUT
    @Path("/{id}/enable")
    @Produces(TEXT_PLAIN)
    @Operation(operationId = "EndpointResource$V3_enableEndpoint", summary = "Enable an endpoint", description = "Enables an endpoint that is disabled so that the endpoint will be executed on the following operations that use the endpoint. An operation must be restarted to use the enabled endpoint.")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response enableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.enableEndpoint(sec, id);
    }

    @DELETE
    @Path("/{id}/enable")
    @Operation(operationId = "EndpointResource$V3_disableEndpoint", summary = "Disable an endpoint", description = "Disables an endpoint so that the endpoint will not be executed after an operation that uses the endpoint is started. An operation that is already running can still execute the endpoint. Disable an endpoint when you want to stop it from running and might want to re-enable it in the future.")
    @APIResponse(responseCode = "204", description = "The integration has been disabled", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public Response disableEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.disableEndpoint(sec, id);
    }

    @GET
    @Path("/{id}/history/{history_id}/details")
    @Produces(APPLICATION_JSON)
    @Operation(operationId = "EndpointResource$V3_getDetailedEndpointHistory", summary = "Retrieve event notification details", description = "Retrieves extended information about the outcome of an event notification related to the specified endpoint. Use this endpoint to learn why an event delivery failed.")
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
    @Operation(operationId = "EndpointResource$V3_testEndpoint", summary = "Generate a test notification", description = "Generates a test notification for a particular endpoint. Use this endpoint to test that an integration that you created works as expected. This endpoint triggers a test notification that should be received by the target recipient. For example, if you set up a webhook as the action to take upon receiving a notification, you should receive a test notification when using this endpoint.")
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

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @Consumes(APPLICATION_JSON)
    @Operation(operationId = "EndpointResource$V3_updateEndpoint", summary = "Update an endpoint", description = "Updates the endpoint configuration. Use this to update an existing endpoint. Any changes to the endpoint take place immediately.")
    @Path("/{id}")
    @Produces(TEXT_PLAIN)
    @PUT
    @Transactional
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    public Response updateEndpoint(
            @Context                                        SecurityContext securityContext,
            @PathParam("id")                                UUID id,
            @RequestBody(required = true) @NotNull @Valid EndpointDTO endpointDTO
    ) {
        final Endpoint endpoint = this.endpointMapperV3.toEntity(endpointDTO);
        return super.commonUpdateEndpoint(securityContext, id, endpoint, endpointDTO.getEventTypes(), false);
    }

    @PUT
    @Path("/{id}/secrets")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(operationId = "EndpointResource$V3_updateEndpointSecrets", summary = "Create or update an endpoint's secrets", description = "Creates or replaces the secrets (secret token, bearer authentication) associated with an endpoint. The secrets are never returned by the API: use this endpoint to set them, and \"DELETE .../secrets\" to clear them. Omitting a field clears that secret.")
    @APIResponse(responseCode = "204", description = "The secrets have been updated", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @APIResponse(responseCode = "400", description = "This endpoint type does not support secrets")
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response updateEndpointSecrets(
            @Context                     final SecurityContext sec,
            @PathParam("id")             final UUID id,
            @NotNull @Valid @RequestBody final EndpointSecretsDTO endpointSecretsDTO
    ) {
        return super.updateEndpointSecrets(sec, id, endpointSecretsDTO.getSecretToken(), endpointSecretsDTO.getBearerAuthentication());
    }

    @DELETE
    @Path("/{id}/secrets")
    @Operation(operationId = "EndpointResource$V3_deleteEndpointSecrets", summary = "Delete an endpoint's secrets", description = "Deletes all the secrets (secret token, bearer authentication) associated with an endpoint.")
    @APIResponse(responseCode = "204", description = "The secrets have been deleted", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @APIResponse(responseCode = "400", description = "This endpoint type does not support secrets")
    @Authorization(legacyRBACRole = RBAC_WRITE_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_EDIT)
    @Transactional
    public Response deleteEndpointSecrets(@Context SecurityContext sec, @PathParam("id") UUID id) {
        return super.deleteEndpointSecrets(sec, id);
    }
}
