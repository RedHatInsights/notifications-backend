package com.redhat.cloud.notifications.connector.splunk;

import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.time.temporal.ChronoUnit;

import static com.redhat.cloud.notifications.connector.v2.http.CommonHttpConstants.JSON_UTF8;

@RegisterRestClient(configKey = "connector-rest-client")
@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2) // 1 initial + 2 retries = 3 attempts
public interface SplunkRestClient {

    @POST
    @Consumes(JSON_UTF8)
    Response post(@HeaderParam("Authorization") String authorization,
                  @Url String url,
                  String body);
}
