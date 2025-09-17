package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @RestClient
    InternalEngine internalEngine;

    @Inject
    TemplateService templateService;

    /**
     * Extracts the relevant information for the email connector from the
     * received message from the engine.
     * @param exchange the incoming exchange from the engine.
     * @param cloudEventData the received Cloud Event.
     */
    @Override
    public void extract(final Exchange exchange, final JsonObject cloudEventData) {

        // Should the "cloudEventData" object contain the payload's identifier, then we
        // need to fetch the original payload's contents from the engine.
        final String payloadId = cloudEventData.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY);
        JsonObject dataToProcess = cloudEventData;
        if (null != payloadId) {
            final PayloadDetails payloadDetails = this.internalEngine.getPayloadDetails(payloadId);

            dataToProcess = new JsonObject(payloadDetails.contents());
            exchange.setProperty(ExchangeProperty.PAYLOAD_ID, payloadId);
        }

        EmailNotification emailNotification = dataToProcess.mapTo(EmailNotification.class);
        final Set<String> emails = emailNotification.recipientSettings().stream()
                .filter(settings -> settings.getEmails() != null)
                .flatMap(settings -> settings.getEmails().stream())
                .collect(toSet());

        final String historyId = exchange.getProperty(ID, String.class);
        exchange.setProperty(ExchangeProperty.RENDERED_BODY,
            renderEmailTemplateFromCommonModule(emailNotification, IntegrationType.EMAIL_BODY, emailNotification.emailBody(), historyId));
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT,
            renderEmailTemplateFromCommonModule(emailNotification, IntegrationType.EMAIL_TITLE, emailNotification.emailSubject(), historyId));
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, emailNotification.recipientSettings());
        exchange.setProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, emailNotification.subscribedByDefault());
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, emailNotification.subscribers());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, emailNotification.unsubscribers());
        exchange.setProperty(ExchangeProperty.RECIPIENTS_AUTHORIZATION_CRITERION, emailNotification.recipientsAuthorizationCriterion());
        exchange.setProperty(ExchangeProperty.EMAIL_RECIPIENTS, emails);
        exchange.setProperty(ExchangeProperty.EMAIL_SENDER, emailNotification.emailSender());
        exchange.setProperty(ExchangeProperty.USE_SIMPLIFIED_EMAIL_ROUTE, emailConnectorConfig.useSimplifiedEmailRoute(emailNotification.orgId()));
    }

    private String renderEmailTemplateFromCommonModule(EmailNotification emailNotification, IntegrationType integrationType, final String renderedContentFromEngine, String historyId) {
        if (null != emailNotification.eventData()) {
            try {
                Map<String, Object> additionalContext = new HashMap<>();
                additionalContext.put("environment", emailNotification.eventData().get("environment"));
                additionalContext.put("pendo_message", emailNotification.eventData().get("pendo_message"));
                additionalContext.put("ignore_user_preferences", emailNotification.eventData().get("ignore_user_preferences"));
                additionalContext.put("action", emailNotification.eventData());
                additionalContext.put("source", emailNotification.eventData().get("source"));

                TemplateDefinition templateDefinition = new TemplateDefinition(
                    integrationType,
                    emailNotification.eventData().get("bundle").toString(),
                    emailNotification.eventData().get("application").toString(),
                    emailNotification.eventData().get("event_type").toString());
                String templatedEvent = templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
                if (!renderedContentFromEngine.equals(templatedEvent)) {
                    Log.errorf("Legacy and new rendered messages are different for %s (history Id %s): '%s' vs. '%s'", integrationType, historyId, renderedContentFromEngine, templatedEvent);
                    return renderedContentFromEngine;
                } else {
                    Log.infof("Legacy and new rendered messages are identical for %s", integrationType);
                    return templatedEvent;
                }
            } catch (Exception ex) {
                Log.errorf(String.format("Error rendering data with common-template module for %s (history Id %s)", integrationType, historyId), ex);
            }
        }
        return renderedContentFromEngine;
    }
}
