package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import javax.inject.Inject;

/*
 * This processor transforms an incoming notification, initially received as JSON data,
 * into a data structure that can be used by the Camel component to send a message.
 */
public abstract class CamelNotificationProcessor implements Processor {

    @Inject
    protected ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        // First, we need to parse the incoming JSON data into a CamelNotification.
        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        CamelNotification commonNotification = objectMapper.readValue(body, CamelNotification.class);

        // Then, we're using fields from the parsed CamelNotification to build the outgoing data.
        exchange.setProperty("orgId", commonNotification.orgId);
        exchange.setProperty("historyId", commonNotification.historyId);
        exchange.setProperty("webhookUrl", commonNotification.webhookUrl);
        addExtraProperties(exchange, body);
        in.setBody(commonNotification.message);

        logProcessingMessage(commonNotification, body);
    }

    protected void logProcessingMessage(final CamelNotification commonNotification, String exchangeBody) throws JsonProcessingException {
        Log.debugf("Processing %s notification [orgId=%s, historyId=%s, webhookUrl=%s]",
            getIntegrationName(), commonNotification.orgId, commonNotification.historyId, commonNotification.webhookUrl);
    }

    protected void addExtraProperties(final Exchange exchange, final String exchangeBody) throws Exception {
    }

    protected String getIntegrationName() {
        throw new IllegalStateException("Integration name must be provided");
    }
}
