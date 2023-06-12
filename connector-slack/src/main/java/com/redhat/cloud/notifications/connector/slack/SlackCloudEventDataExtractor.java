package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;

@ApplicationScoped
public class SlackCloudEventDataExtractor extends CloudEventDataExtractor {

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        exchange.setProperty(TARGET_URL, cloudEventData.getString("webhookUrl"));
        exchange.setProperty(CHANNEL, cloudEventData.getString("channel"));
        exchange.getIn().setBody(cloudEventData.getString("message"));
    }
}
