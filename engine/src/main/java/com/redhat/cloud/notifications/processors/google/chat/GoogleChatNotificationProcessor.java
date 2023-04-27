package com.redhat.cloud.notifications.processors.google.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/*
 * This processor transforms an incoming Google Chat notification, initially received as JSON data,
 * into a data structure that can be used by the Camel HTTP component to send a message to Google Chat.
 */
@ApplicationScoped
public class GoogleChatNotificationProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        // First, we need to parse the incoming JSON data into a GoogleChatNotification.
        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        GoogleChatNotification googleSpacesNotification = objectMapper.readValue(body, GoogleChatNotification.class);

        // Then, we're using fields from the parsed GoogleChatNotification to build the outgoing data.
        exchange.setProperty("orgId", googleSpacesNotification.orgId);
        exchange.setProperty("historyId", googleSpacesNotification.historyId);
        exchange.setProperty("webhookUrl", googleSpacesNotification.webhookUrl);
        in.setBody(googleSpacesNotification.message);

        Log.infof("Processing Google Chat notification [orgId=%s, historyId=%s, webhookUrl=%s]",
                googleSpacesNotification.orgId, googleSpacesNotification.historyId, googleSpacesNotification.webhookUrl);
    }
}
