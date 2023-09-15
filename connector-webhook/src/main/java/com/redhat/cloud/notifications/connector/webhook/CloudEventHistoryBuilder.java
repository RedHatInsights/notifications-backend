package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.DISABLE_ENDPOINT_CLIENT_ERRORS;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.HTTP_STATUS_CODE;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INCREMENT_ENDPOINT_SERVER_ERRORS;

@ApplicationScoped
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    @Override
    public void process(Exchange exchange) throws Exception {
        super.process(exchange);
        Message in = exchange.getIn();
        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        if (exchange.getProperty(INCREMENT_ENDPOINT_SERVER_ERRORS) != null || exchange.getProperty(DISABLE_ENDPOINT_CLIENT_ERRORS) != null) {
            JsonObject data = new JsonObject(cloudEvent.getString("data"));
            data.getJsonObject("details").put(HTTP_STATUS_CODE, exchange.getProperty(HTTP_STATUS_CODE));
            if (exchange.getProperty(DISABLE_ENDPOINT_CLIENT_ERRORS) != null) {
                data.put(DISABLE_ENDPOINT_CLIENT_ERRORS, exchange.getProperty(DISABLE_ENDPOINT_CLIENT_ERRORS));
            } else {
                data.put(INCREMENT_ENDPOINT_SERVER_ERRORS, exchange.getProperty(INCREMENT_ENDPOINT_SERVER_ERRORS));
            }
            cloudEvent.put("data", data.encode());
        }
        in.setBody(cloudEvent.encode());
    }
}
