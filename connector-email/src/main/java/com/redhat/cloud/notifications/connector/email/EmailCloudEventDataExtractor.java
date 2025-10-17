package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.email.model.EmailAggregation;
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

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ENDPOINT_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailCloudEventDataExtractor extends CloudEventDataExtractor {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @RestClient
    InternalEngine internalEngine;

    @Inject
    TemplateService templateService;

    @Inject
    EmailAggregationProcessor emailAggregationProcessor;

    @Inject
    ObjectMapper objectMapper;

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
            Log.debugf("Received payload from engine %s", payloadDetails);
            dataToProcess = new JsonObject(payloadDetails.contents());
            exchange.setProperty(ExchangeProperty.PAYLOAD_ID, payloadId);
            exchange.setProperty(ORG_ID, dataToProcess.getString("org_id"));
            exchange.setProperty(ENDPOINT_ID, dataToProcess.getString("endpoint_id"));
        }

        EmailNotification emailNotification = dataToProcess.mapTo(EmailNotification.class);
        final Set<String> emails = emailNotification.recipientSettings().stream()
                .filter(settings -> settings.getEmails() != null)
                .flatMap(settings -> settings.getEmails().stream())
                .collect(toSet());

        if (emailNotification.isDailyDigest()) {
            renderDailyDigestFromCommonModule(exchange, emailNotification);
        } else {
            exchange.setProperty(ExchangeProperty.RENDERED_BODY,
                renderInstantEmailFromCommonModule(emailNotification, IntegrationType.EMAIL_BODY));
            exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT,
                renderInstantEmailFromCommonModule(emailNotification, IntegrationType.EMAIL_TITLE));
        }
        exchange.setProperty(ExchangeProperty.RECIPIENT_SETTINGS, emailNotification.recipientSettings());
        exchange.setProperty(ExchangeProperty.SUBSCRIBED_BY_DEFAULT, emailNotification.subscribedByDefault());
        exchange.setProperty(ExchangeProperty.SUBSCRIBERS, emailNotification.subscribers());
        exchange.setProperty(ExchangeProperty.UNSUBSCRIBERS, emailNotification.unsubscribers());
        exchange.setProperty(ExchangeProperty.RECIPIENTS_AUTHORIZATION_CRITERION, emailNotification.recipientsAuthorizationCriterion());
        exchange.setProperty(ExchangeProperty.EMAIL_RECIPIENTS, emails);
        exchange.setProperty(ExchangeProperty.EMAIL_SENDER, emailNotification.emailSender());
        exchange.setProperty(ExchangeProperty.USE_SIMPLIFIED_EMAIL_ROUTE, emailConnectorConfig.useSimplifiedEmailRoute(emailNotification.orgId()));
    }

    private void renderDailyDigestFromCommonModule(Exchange exchange, EmailNotification emailNotification) {
        EmailAggregation emailAggregation = objectMapper.convertValue(emailNotification.eventData(), EmailAggregation.class);

        String emailTitle = renderEmailAggregationTitleTemplateFromCommonModule(emailAggregation.bundleDisplayName());
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailTitle);

        if (exchange.getProperty(ORG_ID) == null) {
            exchange.setProperty(ORG_ID, emailNotification.oldOrgId());
        }
        String builtAggregatedEmailBody = emailAggregationProcessor.aggregate(emailAggregation, exchange.getProperty(ORG_ID, String.class), emailTitle);
        exchange.setProperty(ExchangeProperty.RENDERED_BODY, builtAggregatedEmailBody);
    }

    private String renderInstantEmailFromCommonModule(EmailNotification emailNotification, IntegrationType integrationType) {
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
        return templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
    }

    private String renderEmailAggregationTitleTemplateFromCommonModule(String bundleDisplayName) {
        TemplateDefinition templateDefinition = new TemplateDefinition(
            EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE, null, null, null
        );
        Map<String, Object> mapDataTitle = Map.of("source", Map.of("bundle", Map.of("display_name", bundleDisplayName)));
        return templateService.renderTemplateWithCustomDataMap(templateDefinition, mapDataTitle);
    }
}
