package com.redhat.cloud.notifications.connector;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.SUCCESSFUL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class OutgoingCloudEventBuilder implements Processor {

    public static final String CE_SPEC_VERSION = "1.0";
    public static final String CE_TYPE = "com.redhat.console.notifications.history";

    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();

        /*
         * The exchange may contain headers used by a connector to perform a call to an external service.
         * These headers shouldn't be leaked, especially if they contain authorization data, so we're
         * removing all of them for security purposes.
         */
        in.removeHeaders("*");

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
        outgoingCloudEvent.put("time", LocalDateTime.now(UTC).toString());
        // TODO The serialization to JSON shouldn't be needed here. Migrate this later!
        outgoingCloudEvent.put("data", data.toJson());

        in.setBody(outgoingCloudEvent.toJson());
    }
}
