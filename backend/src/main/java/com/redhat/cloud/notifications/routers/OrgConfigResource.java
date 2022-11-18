package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.db.repositories.AggregationCronjobParametersRepository;
import com.redhat.cloud.notifications.models.AggregationCronjobParameters;
import com.redhat.cloud.notifications.routers.models.DailyDigestTimeSettings;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/org-config")
public class OrgConfigResource {

    @Inject
    AggregationCronjobParametersRepository aggregationCronjobParametersRepository;

    @PUT
    @Path("/notification-daily-digest-time-preference")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    @Transactional
    public Response saveDailyDigestTimePreference(@Context SecurityContext sec, @NotNull @Valid DailyDigestTimeSettings dailyDigestTimePreference) {
        String orgId = getOrgId(sec);
        LocalTime expectedTime = null;
        try {
            expectedTime = LocalTime.parse(dailyDigestTimePreference.dailyDigestTimePreference);
        } catch (DateTimeParseException ex) {
            Log.infof("Error parsing '%s' as time for orgId %s", dailyDigestTimePreference.dailyDigestTimePreference, orgId);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, dailyDigestTimePreference.dailyDigestTimePreference);
        aggregationCronjobParametersRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
        return Response.ok().build();
    }

    @DELETE
    @Path("/notification-daily-digest-time-preference")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    @Transactional
    public Response deleteBehaviorGroup(@Context SecurityContext sec) {
        String orgId = getOrgId(sec);
        aggregationCronjobParametersRepository.deleteDailyDigestPreference(orgId);
        return Response.ok().build();
    }

    @GET
    @Path("/notification-daily-digest-time-preference")
    @Produces(APPLICATION_JSON)
    @Operation(hidden = true)
    public Response getDailyDigestTimePreference(@Context SecurityContext sec) {
        String orgId = getOrgId(sec);
        Log.infof("Delete daily digest time preference for orgId %s", orgId);
        AggregationCronjobParameters storedParameters = aggregationCronjobParametersRepository.findJobAggregationCronjobParameters(orgId);
        if (null != storedParameters) {
            DailyDigestTimeSettings dailyDigestTimePreference = new DailyDigestTimeSettings();
            dailyDigestTimePreference.dailyDigestTimePreference = storedParameters.getExpectedRunningTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            return Response.ok(dailyDigestTimePreference).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
