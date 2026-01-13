package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
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

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTEGRATIONS_V_2_0;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_VIEW;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class EndpointResourceV2 extends EndpointResourceCommon {

    @Inject
    NotificationRepository notificationRepository;

    @Path(API_INTEGRATIONS_V_2_0 + "/endpoints")
    public static class V2 extends EndpointResourceV2 {
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
    @Operation(summary = "Retrieve an endpoint", description = "Retrieves the public information associated with an endpoint such as its description, name, and properties.")
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointDTO getEndpoint(@Context SecurityContext sec, @PathParam("id") UUID id) {
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
    @Authorization(legacyRBACRole = RBAC_READ_INTEGRATIONS_ENDPOINTS, workspacePermissions = INTEGRATIONS_VIEW)
    public EndpointPage getEndpoints(
        @Context                SecurityContext sec,
        @BeanParam @Valid       Query query,
        @QueryParam("type")     List<String> targetType,
        @QueryParam("active")   Boolean activeOnly,
        @QueryParam("name")     String name
    ) {
        return internalGetEndpoints(sec, query, targetType, activeOnly, name, true);
    }
}
