package com.redhat.cloud.notifications.mcp;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@RegisterRestClient(configKey = "notifications-backend")
@Retry(delay = 1, delayUnit = ChronoUnit.SECONDS, maxRetries = 2, abortOn = ClientErrorException.class) // 1 initial + 2 retries = 3 attempts
public interface BackendRestClient {

    @GET
    @Path("/api/notifications/v2.0/notifications/severities")
    @Produces(APPLICATION_JSON)
    String getSeverities(@HeaderParam("x-rh-identity") String xRhIdentity);
}
