package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import java.util.Set;

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

        EmailNotification emailNotification =  cloudEventData.mapTo(EmailNotification.class);
        final Set<String> emails = emailNotification.recipientSettings().stream()
                .filter(settings -> settings.getEmails() != null)
                .flatMap(settings -> settings.getEmails().stream())
                .collect(toSet());

        exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailNotification.emailBody());
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailNotification.emailSubject());
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, emailNotification.recipientSettings());
        exchange.setProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, emailNotification.subscribedByDefault());
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, emailNotification.subscribers());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, emailNotification.unsubscribers());
        exchange.setProperty(ExchangeProperty.RECIPIENTS_AUTHORIZATION_CRITERION, emailNotification.recipientsAuthorizationCriterion());
        exchange.setProperty(ExchangeProperty.EMAIL_RECIPIENTS, emails);
        exchange.setProperty(ExchangeProperty.EMAIL_SENDER, emailNotification.emailSender());

        exchange.setProperty(ExchangeProperty.USE_SIMPLIFIED_EMAIL_ROUTE, emailConnectorConfig.useSimplifiedEmailRoute(emailNotification.orgId()));
    }
}
