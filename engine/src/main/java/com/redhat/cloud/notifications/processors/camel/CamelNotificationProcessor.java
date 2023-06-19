package com.redhat.cloud.notifications.processors.camel;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

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
public abstract class CamelNotificationProcessor implements Processor {

    public static final String CLOUD_EVENT_ID = "id";
    public static final String CLOUD_EVENT_TYPE = "type";
    public static final String CLOUD_EVENT_DATA = "data";

    @Override
    public void process(Exchange exchange) throws Exception {

        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(RETURN_SOURCE, getConnectorName());

        // The incoming JSON data is parsed and its fields are used to build the outgoing data.
        Message in = exchange.getIn();

        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        exchange.setProperty(ID, cloudEvent.getString(CLOUD_EVENT_ID));
        exchange.setProperty(TYPE, cloudEvent.getString(CLOUD_EVENT_TYPE));

        // The 'data' field is sent as a String from SmallRye Reactive Messaging.
        JsonObject data = new JsonObject(cloudEvent.getString(CLOUD_EVENT_DATA));
        exchange.setProperty(ORG_ID, data.getString("orgId"));
        exchange.setProperty(WEBHOOK_URL, data.getString("webhookUrl"));
        addExtraProperties(exchange, data);
        in.setBody(data.getString("message"));

        Log.debugf("Processing %s [connector=%s, historyId=%s]",
                cloudEvent, getConnectorName(), exchange.getProperty(ID, String.class));
    }

    protected abstract String getConnectorName();

    protected void addExtraProperties(Exchange exchange, JsonObject notification) {
    }
}
