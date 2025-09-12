package com.redhat.cloud.notifications.connector.email.clients;

import com.redhat.cloud.notifications.connector.email.dto.RecipientsRequest;
import com.redhat.cloud.notifications.connector.email.dto.RecipientsResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for the recipients resolver service.
 * Replaces the Camel HTTP endpoint for resolving email recipients.
 */
@RegisterRestClient
@RegisterClientHeaders
@Path("/internal")
public interface RecipientsResolverClient {

    @POST
    @Path("/recipients-resolver")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    RecipientsResponse resolveRecipients(RecipientsRequest request);
}


