package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class CloudEventHistoryBuilder extends OutgoingCloudEventBuilder {

    @Override
    public void process(Exchange exchange) throws Exception {
        super.process(exchange);

        Message in = exchange.getIn();
        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        JsonObject data = new JsonObject(cloudEvent.getString("data"));
        data.getJsonObject("details").put(ExchangeProperty.RESOLVED_RECIPIENT_LIST, exchange.getProperty(ExchangeProperty.RESOLVED_RECIPIENT_LIST));

        cloudEvent.put("data", data.encode());
        in.setBody(cloudEvent.encode());
    }
}
