package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.repositories.DigestTriggerOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionTypeDTO;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_3_0;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static com.redhat.cloud.notifications.db.repositories.DigestTriggerOrgConfigRepository.computeNextRun;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class OrgConfigResource {

    static final List<Integer> ALLOWED_MINUTES = Arrays.asList(0, 15, 30, 45);

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    @Path(API_NOTIFICATIONS_V_1_0 + "/org-config")
    public static class V1 extends OrgConfigResource {
    }

    @Path(API_NOTIFICATIONS_V_3_0 + "/org-config")
    public static class V3 extends OrgConfigResource {

        private static final List<SubscriptionTypeDTO> ALLOWED_DIGEST_TYPES = List.of(SubscriptionTypeDTO.DAILY, SubscriptionTypeDTO.WEEKLY);

        @Inject
        DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

        @ConfigProperty(name = "notifications.default.weekly.digest.time", defaultValue = "00:30")
        LocalTime defaultWeeklyDigestTime;

        @ConfigProperty(name = "notifications.default.weekly.digest.day", defaultValue = "MONDAY")
        DayOfWeek defaultWeeklyDigestDay;

        @APIResponse(responseCode = "204")
        @APIResponse(responseCode = "400", description = "Invalid time, day, or subscription type")
        @APIResponse(responseCode = "404", description = "Unrecognized subscription type")
        @PUT
        @Path("/digest/trigger-preference/{subscriptionType}")
        @Consumes(APPLICATION_JSON)
        @Operation(summary = "Set digest trigger preference", description = "Creates or updates the UTC digest trigger schedule for the given type. Accepted values for subscriptionType are daily_email and weekly_email. Accepted minute values are 00, 15, 30, and 45. Seconds and nanoseconds are ignored (truncated to the minute). For weekly_email, scheduled_execution_day (MONDAY to SUNDAY) is required.")
        @Parameter(name = "subscriptionType", schema = @Schema(enumeration = {"daily_email", "weekly_email"}))
        @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT, resourceType = "daily_digest")
        public void saveDigestTriggerPreference(@Context SecurityContext sec,
                                                     @PathParam("subscriptionType") SubscriptionTypeDTO subscriptionType,
                                                     @NotNull @Valid DigestTriggerRequest request) {
            String orgId = getOrgId(sec);
            validateSubscriptionType(subscriptionType);
            validateDigestRequest(subscriptionType, request);
            LocalTime truncatedTime = request.getScheduledExecutionTime().truncatedTo(ChronoUnit.MINUTES);
            Log.infof("Update digest trigger preference for orgId %s, type %s, time %s, day %s", orgId, subscriptionType, truncatedTime, request.getScheduledExecutionDay());
            digestTriggerOrgConfigRepository.createOrUpdateDigestPreference(orgId, subscriptionType.toEntity(), truncatedTime, request.getScheduledExecutionDay());
        }

        @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DigestTriggerResponse.class)))
        @APIResponse(responseCode = "404", description = "Unrecognized subscription type")
        @GET
        @Path("/digest/trigger-preference/{subscriptionType}")
        @Produces(APPLICATION_JSON)
        @Operation(summary = "Retrieve digest trigger preference", description = "Retrieves the UTC digest trigger schedule for the given type. Accepted values for subscriptionType are daily_email and weekly_email.")
        @Parameter(name = "subscriptionType", schema = @Schema(enumeration = {"daily_email", "weekly_email"}))
        @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "daily_digest")
        public DigestTriggerResponse getDigestTriggerPreference(@Context SecurityContext sec,
                                                                         @PathParam("subscriptionType") SubscriptionTypeDTO subscriptionType) {
            String orgId = getOrgId(sec);
            validateSubscriptionType(subscriptionType);
            SubscriptionType entityType = subscriptionType.toEntity();
            Log.infof("Get digest trigger preference for orgId %s, type %s", orgId, subscriptionType);
            DigestTriggerOrgConfig config = digestTriggerOrgConfigRepository.findDigestTriggerOrgConfig(orgId, entityType);
            if (config != null) {
                return toResponse(config);
            }
            final LocalTime defaultTime = subscriptionType == SubscriptionTypeDTO.WEEKLY ? defaultWeeklyDigestTime : defaultDailyDigestTime;
            final DayOfWeek defaultDay = subscriptionType == SubscriptionTypeDTO.WEEKLY ? defaultWeeklyDigestDay : null;
            final String cron = DigestTriggerOrgConfigRepository.buildCronExpression(entityType, defaultTime, defaultDay);
            DigestTriggerResponse response = new DigestTriggerResponse();
            response.setSubscriptionType(subscriptionType);
            response.setScheduledExecutionTime(defaultTime);
            response.setScheduledExecutionDay(defaultDay);
            response.setNextRun(computeNextRun(cron));
            return response;
        }

        private void validateSubscriptionType(SubscriptionTypeDTO subscriptionType) {
            if (!ALLOWED_DIGEST_TYPES.contains(subscriptionType)) {
                String allowed = ALLOWED_DIGEST_TYPES.stream()
                    .map(SubscriptionTypeDTO::toWireName)
                    .collect(Collectors.joining(", "));
                throw new BadRequestException("Subscription type must be one of: " + allowed);
            }
        }

        private void validateDigestRequest(SubscriptionTypeDTO subscriptionType, DigestTriggerRequest request) {
            validateMinute(request.getScheduledExecutionTime());
            if (subscriptionType == SubscriptionTypeDTO.WEEKLY) {
                if (request.getScheduledExecutionDay() == null) {
                    throw new BadRequestException("scheduled_execution_day is required for WEEKLY (MONDAY to SUNDAY).");
                }
            } else if (request.getScheduledExecutionDay() != null) {
                throw new BadRequestException("scheduled_execution_day must not be set for DAILY subscriptions.");
            }
        }

        static DigestTriggerResponse toResponse(DigestTriggerOrgConfig config) {
            DigestTriggerResponse response = new DigestTriggerResponse();
            response.setSubscriptionType(SubscriptionTypeDTO.fromEntity(config.getId().subscriptionType));
            response.setScheduledExecutionTime(DigestTriggerOrgConfigRepository.parseTimeFromCron(config.getCronExpression()));
            response.setScheduledExecutionDay(DigestTriggerOrgConfigRepository.parseDayFromCron(config.getCronExpression()));
            response.setNextRun(config.getNextRun());
            return response;
        }
    }

    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "400", description = "Invalid minute value specified")
    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Set the daily digest time", description = "Sets the daily digest UTC time. The accepted minute values are 00, 15, 30, and 45. Seconds and nanoseconds are ignored (truncated to the minute). Use this endpoint to set the time when daily emails are sent.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT, resourceType = "daily_digest")
    public void saveDailyDigestTimePreference(@Context SecurityContext sec, @NotNull LocalTime expectedTime) {
        String orgId = getOrgId(sec);
        expectedTime = expectedTime.truncatedTo(ChronoUnit.MINUTES);
        validateMinute(expectedTime);
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, expectedTime);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
    }

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @GET
    @Path("/daily-digest/time-preference")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the daily digest time", description = "Retrieves the daily digest time setting. Use this endpoint to check the time that daily emails are sent.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "daily_digest")
    public Response getDailyDigestTimePreference(@Context SecurityContext sec) {
        String orgId = getOrgId(sec);
        Log.infof("Get daily digest time preference for orgId %s", orgId);
        AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
        if (null != storedParameters) {
            return Response.ok(storedParameters.getScheduledExecutionTime()).build();
        } else {
            return Response.ok(defaultDailyDigestTime).build();
        }
    }

    void validateMinute(LocalTime time) {
        if (!ALLOWED_MINUTES.contains(time.getMinute())) {
            String errorMessage = "Accepted minute values are: " + ALLOWED_MINUTES.stream().map(min -> String.format("%02d", min)).collect(Collectors.joining(", ")) + ".";
            throw new BadRequestException(errorMessage);
        }
    }

}
