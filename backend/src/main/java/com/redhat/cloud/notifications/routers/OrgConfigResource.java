package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.quarkus.logging.Log;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/org-config")
public class OrgConfigResource {

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Transactional
    @RolesAllowed(ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS)
    public Response saveDailyDigestTimePreference(@Context SecurityContext sec, String dailyDigestTimePreference) {
        String orgId = getOrgId(sec);
        LocalTime expectedTime = null;
        try {
            expectedTime = LocalTime.parse(dailyDigestTimePreference);
        } catch (DateTimeParseException ex) {
            Log.infof("Error parsing '%s' as time for orgId %s", dailyDigestTimePreference, orgId);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, dailyDigestTimePreference);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
        return Response.ok().build();
    }

    @GET
    @Path("/daily-digest/time-preference")
    @Produces(TEXT_PLAIN)
    @RolesAllowed(ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS)
    public Response getDailyDigestTimePreference(@Context SecurityContext sec) {
        String orgId = getOrgId(sec);
        Log.infof("Delete daily digest time preference for orgId %s", orgId);
        AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
        if (null != storedParameters) {
            return Response.ok(storedParameters.getScheduledExecutionTime()).build();
        } else {
            return Response.ok(LocalTime.MIDNIGHT).build();
        }
    }
}
