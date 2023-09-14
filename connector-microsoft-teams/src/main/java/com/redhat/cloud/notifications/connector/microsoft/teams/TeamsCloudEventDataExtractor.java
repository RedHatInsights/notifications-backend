package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

@ApplicationScoped
public class TeamsCloudEventDataExtractor extends CloudEventDataExtractor {

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) {
        exchange.setProperty(TARGET_URL, cloudEventData.getString("webhookUrl"));
        exchange.getIn().setBody(cloudEventData.getString("message"));
    }
}
