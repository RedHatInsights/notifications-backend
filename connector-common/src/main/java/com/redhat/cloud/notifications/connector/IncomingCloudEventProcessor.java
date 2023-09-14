package com.redhat.cloud.notifications.connector;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.RETURN_SOURCE;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TYPE;

@ApplicationScoped
public class IncomingCloudEventProcessor implements Processor {

    public static final String CLOUD_EVENT_ID = "id";
    public static final String CLOUD_EVENT_TYPE = "type";
    public static final String CLOUD_EVENT_DATA = "data";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    CloudEventDataExtractor cloudEventDataExtractor;

    @Override
    public void process(Exchange exchange) throws Exception {

        Message in = exchange.getIn();

        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));

        exchange.setProperty(ID, cloudEvent.getString(CLOUD_EVENT_ID));
        exchange.setProperty(TYPE, cloudEvent.getString(CLOUD_EVENT_TYPE));

        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(RETURN_SOURCE, connectorConfig.getConnectorName());

        JsonObject data = cloudEvent.getJsonObject(CLOUD_EVENT_DATA);
        exchange.setProperty(ORG_ID, data.getString("orgId"));

        cloudEventDataExtractor.extract(exchange, data);
    }
}
