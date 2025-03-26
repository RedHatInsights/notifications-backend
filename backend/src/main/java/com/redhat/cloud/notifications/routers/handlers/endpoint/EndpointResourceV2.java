package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.annotation.IntegrationId;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.project_kessel.api.inventory.v1beta1.resources.ListNotificationsIntegrationsResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getUsername;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_INTEGRATIONS_V_2_0 + "/endpoints")
public class EndpointResourceV2 extends EndpointResource {
    @GET
    @Path("/{id}/history")
    @Produces(APPLICATION_JSON)
    @Parameters(
        {
            @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "Number of items per page, if not specified or 0 is used, returns a maximum of " + MAX_NOTIFICATION_HISTORY_RESULTS + " elements.",
                schema = @Schema(type = SchemaType.INTEGER)
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
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.VIEW_HISTORY})
    public Page<NotificationHistoryDTO> getEndpointHistory(
        @Context SecurityContext sec,
        @Context UriInfo uriInfo,
        @IntegrationId @PathParam("id") UUID id,
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
            PageLinksBuilder.build(uriInfo.getPath(), notificationHistoryCount, query.getLimit().getLimit(), query.getLimit().getOffset()),
            new Meta(notificationHistoryCount)
        );
    }

    @GET
    @Path("/{id}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS, integrationPermissions = {IntegrationPermission.VIEW})
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @IntegrationId @PathParam("id") UUID id) {
        return internalGetEndpoint(sec, id, true);
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List endpoints", description = "Provides a list of endpoints. Use this endpoint to find specific endpoints.")
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
            )
        }
    )
    public EndpointPage getEndpoints(
        @Context                SecurityContext sec,
        @BeanParam @Valid       Query query,
        @QueryParam("type")     List<String> targetType,
        @QueryParam("active")   Boolean activeOnly,
        @QueryParam("name")     String name
    ) {
        Set<UUID> authorizedIds = null;
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            // Fetch the set of integration IDs the user is authorized to view.
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

            Log.errorf("[org_id: %s][username: %s] Kessel did not return any integration IDs for the request", getOrgId(sec), getUsername(sec));

            // add permission as argument -- rather than assuming it underneath
            final Multi<ListNotificationsIntegrationsResponse> responseMulti = this.kesselAssets.listIntegrations(sec, workspaceId.toString());
            authorizedIds = responseMulti.map(ListNotificationsIntegrationsResponse::getIntegrations)
                    .map(i -> i.getReporterData().getLocalResourceId())
                    .map(UUID::fromString)
                    .collect()
                    .asSet()
                    .await().indefinitely();

            if (authorizedIds.isEmpty()) {
                Log.infof("[org_id: %s][username: %s] Kessel did not return any integration IDs for the request", getOrgId(sec), getUsername(sec));

                return new EndpointPage(new ArrayList<>(), new HashMap<>(), new Meta(0L));
            }
        } else {
            // Legacy RBAC permission checking. The permission will have been
            // prefetched and processed by the "ConsoleIdentityProvider".
            if (!sec.isUserInRole(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)) {
                throw new ForbiddenException();
            }
        }

        return internalGetEndpoints(sec, query, targetType, activeOnly, name, authorizedIds, true);
    }
}
