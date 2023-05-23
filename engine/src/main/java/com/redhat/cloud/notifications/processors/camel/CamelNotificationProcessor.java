package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.HISTORY_ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SOURCE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;

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

        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(SOURCE, getSource());

        // Type of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(TYPE, "com.redhat.console.notifications.toCamel." + getSource());

        // Then, we're using fields from the parsed CamelNotification to build the outgoing data.
        exchange.setProperty(ORG_ID, commonNotification.orgId);
        exchange.setProperty(HISTORY_ID, commonNotification.historyId);
        exchange.setProperty(WEBHOOK_URL, commonNotification.webhookUrl);
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

    protected abstract String getSource();
}
