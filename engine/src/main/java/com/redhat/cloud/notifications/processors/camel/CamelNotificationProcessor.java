package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;

/*
 * This processor transforms an incoming notification, initially received as JSON data,
 * into a data structure that can be used by the Camel component to send a message.
 */
public abstract class CamelNotificationProcessor<T extends CamelNotification> implements Processor {

    public static final String CLOUD_EVENT_ID_HEADER = "ce-id";
    public static final String CLOUD_EVENT_TYPE_HEADER = "ce-type";

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();
        exchange.setProperty(ID, in.getHeader(CLOUD_EVENT_ID_HEADER, String.class));
        exchange.setProperty(TYPE, in.getHeader(CLOUD_EVENT_TYPE_HEADER, String.class));

        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(RETURN_SOURCE, getConnectorName());

        // The incoming JSON data is parsed and its fields are used to build the outgoing data.
        String body = in.getBody(String.class);
        T notification = objectMapper.readValue(body, getNotificationClass());
        exchange.setProperty(ORG_ID, notification.orgId);
        exchange.setProperty(WEBHOOK_URL, notification.webhookUrl);
        addExtraProperties(exchange, notification);
        in.setBody(notification.message);

        Log.debugf("Processing %s [connector=%s, historyId=%s]",
                notification, getConnectorName(), exchange.getProperty(ID, String.class));
    }

    protected Class<T> getNotificationClass() {
        return (Class<T>) CamelNotification.class;
    }

    protected abstract String getConnectorName();

    protected void addExtraProperties(Exchange exchange, T notification) {
    }
}
