package com.redhat.cloud.notifications.connector.email.clients;

import com.redhat.cloud.notifications.connector.email.dto.BOPRequest;
import com.redhat.cloud.notifications.connector.email.dto.BOPResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for the BOP (Business Operations Platform) service.
 * Replaces the Camel HTTP endpoint for sending emails.
 */
@RegisterRestClient
@RegisterClientHeaders
@Path("/api")
public interface BOPClient {

    @POST
    @Path("/send-email")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    BOPResponse sendEmail(BOPRequest request);
}


