package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_2_0;
import static com.redhat.cloud.notifications.db.Query.DEFAULT_RESULTS_PER_PAGE;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class NotificationResourceV2 {

    @Inject
    BackendConfig backendConfig;

    @Inject
    WorkspaceUtils workspaceUtils;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Path(API_NOTIFICATIONS_V_2_0 + "/notifications")
    public static class V2 extends NotificationResourceV2 {
    }

    @GET
    @Path("/eventTypes/{eventTypeId}/behaviorGroups")
    @Produces(APPLICATION_JSON)
    @Parameter(
        name = "limit",
        in = ParameterIn.QUERY,
        description = "Number of items per page, if not specified " + DEFAULT_RESULTS_PER_PAGE + " is used.",
        schema = @Schema(type = SchemaType.INTEGER, defaultValue = DEFAULT_RESULTS_PER_PAGE + "")
    )
    @Operation(summary = "Retrieve the behavior groups linked to an event type.")
    public Page<BehaviorGroup> getLinkedBehaviorGroups(
        @Context SecurityContext sec,
        @PathParam("eventTypeId") UUID eventTypeId,
        @BeanParam @Valid Query query,
        @Context UriInfo uriInfo
    ) {
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));
            if (backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(getOrgId(sec))) {
                this.kesselInventoryAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.EVENT_TYPES_VIEW, workspaceId);
                this.kesselInventoryAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, workspaceId);
            } else {
                this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.EVENT_TYPES_VIEW, workspaceId);
                this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.BEHAVIOR_GROUPS_VIEW, workspaceId);
            }

            return this.internalGetLinkedBehaviorGroups(sec, eventTypeId, query, uriInfo);
        } else {
            return this.legacyRBACGetLinkedBehaviorGroups(sec, eventTypeId, query, uriInfo);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Page<BehaviorGroup> legacyRBACGetLinkedBehaviorGroups(final SecurityContext securityContext, final UUID eventTypeId, @Valid final Query query, final UriInfo uriInfo) {
        return this.internalGetLinkedBehaviorGroups(securityContext, eventTypeId, query, uriInfo);
    }

    public Page<BehaviorGroup> internalGetLinkedBehaviorGroups(final SecurityContext securityContext, final UUID eventTypeId, @Valid final Query query, final UriInfo uriInfo) {
        String orgId = getOrgId(securityContext);

        final List<BehaviorGroup> behaviorGroups = this.behaviorGroupRepository.findBehaviorGroupsByEventTypeId(orgId, eventTypeId, query);
        final long behaviorGroupCount = this.behaviorGroupRepository.countByEventTypeId(orgId, eventTypeId);

        return new Page<>(
            behaviorGroups,
            PageLinksBuilder.build(uriInfo.getPath(), behaviorGroupCount, query),
            new Meta(behaviorGroupCount)
        );
    }
}
