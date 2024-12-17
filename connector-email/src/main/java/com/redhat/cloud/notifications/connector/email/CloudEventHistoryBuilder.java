package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.http.HttpOutgoingCloudEventBuilder;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class CloudEventHistoryBuilder extends HttpOutgoingCloudEventBuilder {

    public static final String TOTAL_RECIPIENTS_KEY = "total_recipients";

    @Override
    public void process(Exchange exchange) throws Exception {
        super.process(exchange);
        int totalRecipients = exchange.getProperty(TOTAL_RECIPIENTS_KEY, 0, Integer.class);

        Message in = exchange.getIn();
        JsonObject cloudEvent = new JsonObject(in.getBody(String.class));
        JsonObject data = new JsonObject(cloudEvent.getString("data"));
        data.getJsonObject("details").put(TOTAL_RECIPIENTS_KEY, totalRecipients);

        cloudEvent.put("data", data.encode());
        in.setBody(cloudEvent.encode());
    }
}
