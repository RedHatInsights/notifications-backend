package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.Body;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for both BOP and MBOP services, which in turn, talk to the IT
 * service.
 */
@RegisterRestClient(configKey = "bop")
public interface BOPService {

    @Path("/v1/sendEmails")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void sendEmail(
        @HeaderParam(Constants.BOP_API_TOKEN_HEADER)    String apiToken,
        @HeaderParam(Constants.BOP_CLIENT_ID_HEADER)    String clientId,
        @HeaderParam(Constants.BOP_ENV_HEADER)          String environment,
        @Body SendEmailsRequest sendEmailsRequest);
}
