package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Extracts the relevant information for the email connector from the
     * received message from the engine.
     * @param exchange the incoming exchange from the engine.
     * @param cloudEventData the received Cloud Event.
     */
    @Override
    public void extract(final Exchange exchange, final JsonObject cloudEventData) {

        final List<RecipientSettings> recipientSettings = cloudEventData.getJsonArray("recipient_settings")
            .stream()
            .map(JsonObject.class::cast)
            .map(jsonSetting -> jsonSetting.mapTo(RecipientSettings.class))
            .toList();

        final Set<String> subscribers = cloudEventData.getJsonArray("subscribers", JsonArray.of())
            .stream()
            .map(String.class::cast)
            .collect(toSet());

        final Set<String> unsubscribers = cloudEventData.getJsonArray("unsubscribers", JsonArray.of())
                .stream()
                .map(String.class::cast)
                .collect(toSet());

        final Set<String> emails = recipientSettings.stream()
                .filter(settings -> settings.getEmails() != null)
                .flatMap(settings -> settings.getEmails().stream())
                .collect(toSet());

        exchange.setProperty(ExchangeProperty.RENDERED_BODY, cloudEventData.getString("email_body"));
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, cloudEventData.getString("email_subject"));
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, cloudEventData.getBoolean("subscribed_by_default"));
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, subscribers);
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, unsubscribers);
        exchange.setProperty(ExchangeProperty.EMAIL_RECIPIENTS, emails);
        exchange.setProperty(ExchangeProperty.EMAIL_SENDER, cloudEventData.getString("email_sender"));

        exchange.setProperty(ExchangeProperty.USE_EMAIL_BOP_V1_SSL, emailConnectorConfig.isEnableBopServiceWithSslChecks(exchange.getProperty(ORG_ID, String.class)));
    }
}
