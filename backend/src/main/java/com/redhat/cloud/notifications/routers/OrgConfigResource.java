package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/org-config")
public class OrgConfigResource {

    static final List<Integer> ALLOWED_MINUTES = Arrays.asList(0, 15, 30, 45);

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notification.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    @Operation(summary = "Save daily digest time preference as UTC format. To cover all time zone conversions to UTC format, minute value have to be 00, 15, 30 or 45.")
    public Response saveDailyDigestTimePreference(@Context SecurityContext sec, @NotNull LocalTime expectedTime) {
        String orgId = getOrgId(sec);
        if (!ALLOWED_MINUTES.contains(expectedTime.getMinute())) {
            String errorMessage = "Accepted minute values are: " + ALLOWED_MINUTES.stream().map(min -> String.format("%02d", min)).collect(Collectors.joining(", ")) + ".";
            return Response.status(Response.Status.BAD_REQUEST).entity(errorMessage).build();
        }
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, expectedTime);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
        return Response.ok().build();
    }

    @GET
    @Path("/daily-digest/time-preference")
    @Produces(APPLICATION_JSON)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
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
}
