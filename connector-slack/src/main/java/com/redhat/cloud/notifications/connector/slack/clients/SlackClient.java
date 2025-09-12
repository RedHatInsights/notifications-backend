package com.redhat.cloud.notifications.connector.slack.clients;

import com.redhat.cloud.notifications.connector.slack.dto.SlackRequest;
import com.redhat.cloud.notifications.connector.slack.dto.SlackResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Slack API.
 * Replaces the Camel HTTP endpoint for sending Slack messages.
 */
@RegisterRestClient
@RegisterClientHeaders
public interface SlackClient {

    @POST
    @Path("/slack")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    SlackResponse sendMessage(SlackRequest request);
}


