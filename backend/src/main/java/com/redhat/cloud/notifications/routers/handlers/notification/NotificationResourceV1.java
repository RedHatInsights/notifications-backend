package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/notifications")
public class NotificationResourceV1 extends NotificationResource {
    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List the behavior groups linked to an event type", description = "Lists the behavior groups that are linked to an event type. Use this endpoint to see which behavior groups will be affected if you delete an event type.")
    public List<BehaviorGroup> getLinkedBehaviorGroups(
        @Context SecurityContext sec,
        @PathParam("eventTypeId") UUID eventTypeId,
        @BeanParam @Valid Query query
    ) {
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));
            this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.EVENT_TYPES_VIEW, workspaceId);
            this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, workspaceId);

            return this.internalGetLinkedBehaviorGroups(sec, eventTypeId, query);
        } else {
            return this.legacyRBACGetLinkedBehaviorGroups(sec, eventTypeId, query);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public List<BehaviorGroup> legacyRBACGetLinkedBehaviorGroups(final SecurityContext securityContext, final UUID eventTypeId, @Valid final Query query) {
        return this.internalGetLinkedBehaviorGroups(securityContext, eventTypeId, query);
    }

    public List<BehaviorGroup> internalGetLinkedBehaviorGroups(final SecurityContext securityContext, final UUID eventTypeId, @Valid final Query query) {
        String orgId = getOrgId(securityContext);

        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(orgId, eventTypeId, query);
    }
}
