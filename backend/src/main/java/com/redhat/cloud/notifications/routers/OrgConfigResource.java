package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalTime;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/org-config")
public class OrgConfigResource {

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notification.default.daily.digest.hour", defaultValue = "0")
    int defaultDailyDigestHour;

    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Response saveDailyDigestTimePreference(@Context SecurityContext sec, @NotNull LocalTime expectedTime) {
        String orgId = getOrgId(sec);
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
            return Response.ok(LocalTime.of(defaultDailyDigestHour, 0)).build();
        }
    }
}
