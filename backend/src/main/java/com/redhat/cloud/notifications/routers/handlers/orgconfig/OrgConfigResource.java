package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class OrgConfigResource {
    @Inject
    BackendConfig backendConfig;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    WorkspaceUtils workspaceUtils;

    static final List<Integer> ALLOWED_MINUTES = Arrays.asList(0, 15, 30, 45);

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    @Path(Constants.API_NOTIFICATIONS_V_1_0 + "/org-config")
    static class V1 extends OrgConfigResource {

    }

    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "400", description = "Invalid minute value specified")
    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Set the daily digest time", description = "Sets the daily digest UTC time. The accepted minute values are 00, 15, 30, and 45. Use this endpoint to set the time when daily emails are sent.")
    public void saveDailyDigestTimePreference(@Context SecurityContext sec, LocalTime expectedTime) {
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

            this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.DAILY_DIGEST_PREFERENCE_EDIT, workspaceId);

            this.internalSaveDailyDigestTimePreference(sec, expectedTime);
        } else {
            this.legacyRBACInternalSaveDailyDigestTimePreference(sec, expectedTime);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public void legacyRBACInternalSaveDailyDigestTimePreference(final SecurityContext securityContext, final LocalTime expectedTime) {
        this.internalSaveDailyDigestTimePreference(securityContext, expectedTime);
    }

    public void internalSaveDailyDigestTimePreference(final SecurityContext securityContext, @NotNull final LocalTime expectedTime) {
        String orgId = getOrgId(securityContext);
        if (!ALLOWED_MINUTES.contains(expectedTime.getMinute())) {
            String errorMessage = "Accepted minute values are: " + ALLOWED_MINUTES.stream().map(min -> String.format("%02d", min)).collect(Collectors.joining(", ")) + ".";

            throw new BadRequestException(errorMessage);
        }
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, expectedTime);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
    }

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @GET
    @Path("/daily-digest/time-preference")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the daily digest time", description = "Retrieves the daily digest time setting. Use this endpoint to check the time that daily emails are sent.")
    public Response getDailyDigestTimePreference(@Context SecurityContext sec) {
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(sec))) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(sec));

            this.kesselAuthorization.hasPermissionOnWorkspace(sec, WorkspacePermission.DAILY_DIGEST_PREFERENCE_VIEW, workspaceId);

            return this.internalGetDailyDigestTimePreference(sec);
        } else {
            return this.legacyRBACGetDailyDigestTimePreference(sec);
        }
    }

    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Response legacyRBACGetDailyDigestTimePreference(final SecurityContext securityContext) {
        return this.internalGetDailyDigestTimePreference(securityContext);
    }

    public Response internalGetDailyDigestTimePreference(final SecurityContext securityContext) {
        String orgId = getOrgId(securityContext);
        Log.infof("Get daily digest time preference for orgId %s", orgId);
        AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
        if (null != storedParameters) {
            return Response.ok(storedParameters.getScheduledExecutionTime()).build();
        } else {
            return Response.ok(defaultDailyDigestTime).build();
        }
    }
}
