package com.redhat.cloud.notifications.routers.engine;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequestDto;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

@RegisterRestClient(configKey = "internal-daily-digest")
public interface DailyDigestService {

    /**
     * Sends a "trigger daily digest" job to the engine.
     * @param triggerDailyDigestRequestDto the daily digest's payload with the
     *                                     desired settings.
     */
    @Path(Constants.API_INTERNAL + "/daily-digest")
    @POST
    @Retry(maxRetries = 3)
    void triggerDailyDigest(TriggerDailyDigestRequestDto triggerDailyDigestRequestDto);
}
