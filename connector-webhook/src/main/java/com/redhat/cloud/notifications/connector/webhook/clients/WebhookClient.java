package com.redhat.cloud.notifications.connector.webhook.clients;

import com.redhat.cloud.notifications.connector.webhook.dto.WebhookRequest;
import com.redhat.cloud.notifications.connector.webhook.dto.WebhookResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for webhook delivery.
 * Replaces the Camel HTTP endpoint for sending webhooks.
 */
@RegisterRestClient
@RegisterClientHeaders
public interface WebhookClient {

    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    WebhookResponse sendWebhook(WebhookRequest request);
}


