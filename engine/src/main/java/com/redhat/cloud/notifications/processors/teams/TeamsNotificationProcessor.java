package com.redhat.cloud.notifications.processors.teams;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/*
 * This processor transforms an incoming Teams notification, initially received as JSON data,
 * into a data structure that can be used by the Camel HTTP component to send a message to
 * the desired Teams channel.
 */
@ApplicationScoped
public class TeamsNotificationProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        // First, we need to parse the incoming JSON data into a TeamsNotification.
        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        TeamsNotification teamsNotification = objectMapper.readValue(body, TeamsNotification.class);

        // Then, we're using fields from the parsed TeamsNotification to build the outgoing data.
        exchange.setProperty("orgId", teamsNotification.orgId);
        exchange.setProperty("historyId", teamsNotification.historyId);
        exchange.setProperty("webhookUrl", teamsNotification.webhookUrl);
        in.setBody(teamsNotification.message);

        Log.infof("Processing Teams notification [orgId=%s, historyId=%s, webhookUrl=%s]",
                teamsNotification.orgId, teamsNotification.historyId, teamsNotification.webhookUrl);
    }
}
