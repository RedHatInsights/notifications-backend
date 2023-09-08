package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;

import java.util.List;

@ApplicationScoped
public class EmailCloudEventDataExtractor extends CloudEventDataExtractor {
    /**
     * Extracts the relevant information for the email connector from the
     * received message from the engine.
     * @param exchange the incoming exchange from the engine.
     * @param cloudEventData the received Cloud Event.
     */
    @Override
    public void extract(final Exchange exchange, final JsonObject cloudEventData) {
        final JsonArray settings = cloudEventData.getJsonArray("recipient_settings");

        final List<RecipientSettings> recipientSettings = settings
            .stream()
            .map(JsonObject.class::cast)
            .map(jsonSetting -> jsonSetting.mapTo(RecipientSettings.class))
            .toList();

        final JsonArray subs = cloudEventData.getJsonArray("subscribers");
        final List<String> subscribers = subs
            .stream()
            .map(String.class::cast)
            .toList();

        exchange.setProperty(ExchangeProperty.RENDERED_BODY, cloudEventData.getString("email_body"));
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, cloudEventData.getString("email_subject"));
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);
    }
}
