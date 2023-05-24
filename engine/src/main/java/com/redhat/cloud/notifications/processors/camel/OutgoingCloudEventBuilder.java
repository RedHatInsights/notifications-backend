package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.HISTORY_ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SOURCE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.TYPE;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;

@ApplicationScoped
public class OutgoingCloudEventBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();

        JsonObject details = new JsonObject();
        details.put("type", exchange.getProperty(TYPE, String.class));
        details.put("target", exchange.getProperty(WEBHOOK_URL, String.class));
        details.put("outcome", exchange.getProperty(OUTCOME, String.class));

        JsonObject data = new JsonObject();
        data.put("successful", exchange.getProperty(SUCCESSFUL, Boolean.class));
        data.put("duration", System.currentTimeMillis() - exchange.getProperty(START_TIME, Long.class));
        data.put("details", details);

        JsonObject outgoingCloudEvent = new JsonObject();
        outgoingCloudEvent.put("type", CE_TYPE);
        outgoingCloudEvent.put("specversion", CE_SPEC_VERSION);
        outgoingCloudEvent.put("source", exchange.getProperty(SOURCE, String.class));
        outgoingCloudEvent.put("id", exchange.getProperty(HISTORY_ID, String.class));
        outgoingCloudEvent.put("time", LocalDateTime.now(ZoneOffset.UTC).toString());
        // TODO The serialization to JSON shouldn't be needed here. Migrate this later!
        outgoingCloudEvent.put("data", data.toJson());

        in.setBody(outgoingCloudEvent.toJson());
    }
}
