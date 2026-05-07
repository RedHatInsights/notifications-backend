package com.redhat.cloud.notifications.connector.pagerduty;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.time.temporal.ChronoUnit;

import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.JSON_UTF8;

@RegisterRestClient(configKey = "connector-rest-client")
// 1 initial + 2 retries = 3 total attempts
@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2)
public interface PagerDutyRestClient {

    @POST
    @Consumes(JSON_UTF8)
    Response post(String body);
}
