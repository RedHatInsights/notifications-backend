package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.dto.v1.NotificationHistoryDTO;
import jakarta.annotation.security.RolesAllowed;
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
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.db.repositories.NotificationRepository.MAX_NOTIFICATION_HISTORY_RESULTS;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_INTEGRATIONS_V_1_0 + "/endpoints")
public class EndpointResourceV1 extends EndpointResource {
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
    public List<NotificationHistoryDTO> getEndpointHistory(@Context SecurityContext sec, @PathParam("id") UUID id, @QueryParam("includeDetail") Boolean includeDetail, @BeanParam Query query) {
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            this.kesselAuthorization.hasPermissionOnIntegration(sec, IntegrationPermission.VIEW_HISTORY, id);

            return this.internalGetEndpointHistory(sec, id, includeDetail, query);
        } else {
            return this.legacyRBACGetEndpointHistory(sec, id, includeDetail, query);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_INTEGRATIONS_ENDPOINTS)
    protected List<NotificationHistoryDTO> legacyRBACGetEndpointHistory(final SecurityContext securityContext, final UUID id, final Boolean includeDetail, final Query query) {
        return this.internalGetEndpointHistory(securityContext, id, includeDetail, query);
    }

    protected List<NotificationHistoryDTO> internalGetEndpointHistory(final SecurityContext securityContext, final UUID id, final Boolean includeDetail, @Valid final Query query) {
        if (!this.endpointRepository.existsByUuidAndOrgId(id, getOrgId(securityContext))) {
            throw new NotFoundException("Endpoint not found");
        }

        // TODO We need globally limitations (Paging support and limits etc)
        String orgId = getOrgId(securityContext);
        boolean doDetail = includeDetail != null && includeDetail;
        return commonMapper.notificationHistoryListToNotificationHistoryDTOList(notificationRepository.getNotificationHistory(orgId, id, doDetail, query));
    }
}
