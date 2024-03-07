package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;

@ApplicationScoped
public class SlackCloudEventDataExtractor extends CloudEventDataExtractor {

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        exchange.setProperty(TARGET_URL, cloudEventData.getString("webhookUrl"));
        JsonObject message = new JsonObject();
        message.put("text", cloudEventData.getString("message"));
        if (cloudEventData.getValue("channel") != null) {
            message.put("channel", cloudEventData.getString("channel"));
        }
        exchange.getIn().setBody(message.encode());
    }
}
