package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.connector.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORIGINAL_CLOUD_EVENT;
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

    @RestClient
    InternalEngine internalEngine;

    @Override
    public void process(Exchange exchange) throws Exception {
        JsonObject cloudEvent = new JsonObject(exchange.getIn().getBody(String.class));

        exchange.setProperty(ORIGINAL_CLOUD_EVENT, exchange.getIn().getBody());

        exchange.setProperty(ID, cloudEvent.getString(CLOUD_EVENT_ID));
        exchange.setProperty(TYPE, cloudEvent.getString(CLOUD_EVENT_TYPE));

        // This property will be used later to determine the invocation time.
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        // Source of the Cloud Event returned to the Notifications engine.
        exchange.setProperty(RETURN_SOURCE, connectorConfig.getConnectorName());

        JsonObject data = cloudEvent.getJsonObject(CLOUD_EVENT_DATA);

        // Should the "data" object contain the payload's identifier, then we
        // need to fetch the original payload's contents from the engine.
        final String payloadId = data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY);
        if (null != payloadId) {
            final PayloadDetails payloadDetails = this.internalEngine.getPayloadDetails(payloadId);

            data = new JsonObject(payloadDetails.contents());
            exchange.setProperty(ExchangeProperty.PAYLOAD_ID, payloadId);
        }

        exchange.setProperty(ORG_ID, data.getString("org_id"));

        cloudEventDataExtractor.extract(exchange, data);
    }
}
