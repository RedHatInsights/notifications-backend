package com.redhat.cloud.notifications.connector;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;

@ApplicationScoped
public class OutgoingCloudEventBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();

        JsonObject details = new JsonObject();
        details.put("type", exchange.getProperty(TYPE, String.class));
        details.put("target", exchange.getProperty(TARGET_URL, String.class));
        details.put("outcome", exchange.getProperty(OUTCOME, String.class));

        JsonObject data = new JsonObject();
        data.put("successful", exchange.getProperty(SUCCESSFUL, Boolean.class));
        data.put("duration", System.currentTimeMillis() - exchange.getProperty(START_TIME, Long.class));
        data.put("details", details);

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("source", exchange.getProperty(RETURN_SOURCE, String.class));
        outgoingCloudEvent.put("id", exchange.getProperty(ID, String.class));
        outgoingCloudEvent.put("time", LocalDateTime.now(ZoneOffset.UTC).toString());
        outgoingCloudEvent.put("data", data.toJson());

        in.setBody(outgoingCloudEvent.toJson());
    }
}
