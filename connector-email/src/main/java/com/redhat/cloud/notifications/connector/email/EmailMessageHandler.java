package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.email.model.EmailAggregation;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.HandledEmailMessageDetails;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPManager;
import com.redhat.cloud.notifications.connector.email.processors.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailMessageHandler extends MessageHandler {

    static final String BOP_RESPONSE_TIME_METRIC = "email.bop.response.time";
    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "email.recipients_resolver.response.time";

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

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    BOPManager bopManager;

    @Inject
    MeterRegistry meterRegistry;

    private Timer bopResponseTimer;
    private Timer recipientsResolverResponseTimer;

    @PostConstruct
    void init() {
        bopResponseTimer = Timer.builder(BOP_RESPONSE_TIME_METRIC)
            .register(meterRegistry);
        recipientsResolverResponseTimer = Timer.builder(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC)
            .register(meterRegistry);
    }

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        final HandledEmailMessageDetails handledMessageDetails = new HandledEmailMessageDetails();

        // Phase 1: Extract and resolve payload
        final EmailNotification emailNotification = extractPayload(incomingCloudEvent.getData(), handledMessageDetails);

        // Phase 2: Render templates
        String renderedSubject;
        String renderedBody;
        final String orgId = emailNotification.orgId();
        if (emailNotification.isDailyDigest()) {
            EmailAggregation emailAggregation = objectMapper.convertValue(emailNotification.eventData(), EmailAggregation.class);
            renderedSubject = renderEmailAggregationTitle(emailAggregation.bundleDisplayName());
            renderedBody = emailAggregationProcessor.aggregate(emailAggregation, orgId, renderedSubject);
        } else {
            renderedBody = renderInstantEmail(emailNotification, IntegrationType.EMAIL_BODY);
            renderedSubject = renderInstantEmail(emailNotification, IntegrationType.EMAIL_TITLE);
        }

        // Phase 3: Resolve recipients
        Set<String> emails = extractDirectEmails(emailNotification);
        Set<String> recipientsList = resolveRecipients(orgId, emailNotification);

        if (emailConnectorConfig.isEmailsInternalOnlyEnabled()) {
            Set<String> forbiddenEmail = emails.stream()
                .filter(email -> !email.trim().toLowerCase().endsWith("@redhat.com"))
                .collect(Collectors.toSet());
            if (!forbiddenEmail.isEmpty()) {
                Log.warnf(" %s emails are forbidden for message historyId: %s ", forbiddenEmail, incomingCloudEvent.getId());
            }
            emails.removeAll(forbiddenEmail);
        }
        recipientsList.addAll(emails);
        int totalRecipients = recipientsList.size();

        // Phase 4: Send to BOP
        if (recipientsList.isEmpty()) {
            Log.infof("Skipped Email notification because the recipients list was empty [orgId=%s, historyId=%s]",
                orgId, incomingCloudEvent.getId());
        } else {
            sendToBop(recipientsList, renderedSubject, renderedBody,
                emailNotification.emailSender(), orgId, incomingCloudEvent.getId());
        }

        handledMessageDetails.totalRecipients = totalRecipients;
        handledMessageDetails.outcomeMessage = "Ok";
        return handledMessageDetails;
    }

    EmailNotification extractPayload(final JsonObject cloudEventData, final HandledEmailMessageDetails handledMessageDetails) {
        String payloadId = cloudEventData.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY);
        JsonObject dataToProcess = cloudEventData;

        if (payloadId != null) {
            try {
                UUID.fromString(payloadId);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid payload ID format (expected UUID): " + payloadId, e);
            }
            handledMessageDetails.payloadId = payloadId;
            PayloadDetails payloadDetails = internalEngine.getPayloadDetails(payloadId);
            if (payloadDetails == null || payloadDetails.contents() == null) {
                throw new IllegalStateException("Engine returned null payload for ID: " + payloadId);
            }
            Log.debugf("Received payload from engine %s", payloadDetails);
            try {
                dataToProcess = new JsonObject(payloadDetails.contents());
            } catch (io.vertx.core.json.DecodeException e) {
                throw new IllegalStateException("Engine returned invalid JSON payload for ID: " + payloadId, e);
            }
        }

        try {
            return dataToProcess.mapTo(EmailNotification.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse email notification" + (payloadId != null ? " for payload ID: " + payloadId : ""), e);
        }
    }

    private String renderInstantEmail(EmailNotification emailNotification, IntegrationType integrationType) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", emailNotification.eventData().get("environment"));
        additionalContext.put("pendo_message", emailNotification.eventData().get("pendo_message"));
        additionalContext.put("ignore_user_preferences", emailNotification.eventData().get("ignore_user_preferences"));
        additionalContext.put("action", emailNotification.eventData());
        additionalContext.put("source", emailNotification.eventData().get("source"));

        String bundle = requireEventDataField(emailNotification, "bundle");
        String application = requireEventDataField(emailNotification, "application");
        String eventType = requireEventDataField(emailNotification, "event_type");
        boolean useBetaTemplate = emailConnectorConfig.isUseBetaTemplatesEnabled(
            emailNotification.orgId(), bundle, application, eventType);

        TemplateDefinition templateDefinition = new TemplateDefinition(
            integrationType, bundle, application, eventType, useBetaTemplate);
        return templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
    }

    private String renderEmailAggregationTitle(String bundleDisplayName) {
        TemplateDefinition templateDefinition = new TemplateDefinition(
            EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE, null, null, null);
        Map<String, Object> mapDataTitle = Map.of("source", Map.of("bundle", Map.of("display_name", bundleDisplayName)));
        return templateService.renderTemplateWithCustomDataMap(templateDefinition, mapDataTitle);
    }

    private Set<String> extractDirectEmails(EmailNotification emailNotification) {
        return emailNotification.recipientSettings().stream()
            .filter(settings -> settings.getEmails() != null)
            .flatMap(settings -> settings.getEmails().stream())
            .collect(toSet());
    }

    private Set<String> resolveRecipients(String orgId, EmailNotification emailNotification) {
        Timer.Sample recipientsResolverResponseTimeMetric = Timer.start(meterRegistry);
        Set<String> recipientsList = externalRecipientsResolver.recipientUsers(
                orgId,
                new HashSet<>(emailNotification.recipientSettings()),
                new HashSet<>(emailNotification.subscribers()),
                new HashSet<>(emailNotification.unsubscribers()),
                emailNotification.subscribedByDefault(),
                emailNotification.recipientsAuthorizationCriterion())
            .stream().map(User::getEmail).filter(email -> email != null && !email.isBlank()).collect(toSet());
        recipientsResolverResponseTimeMetric.stop(recipientsResolverResponseTimer);
        return recipientsList;
    }

    private void sendToBop(Set<String> recipientsList, String subject, String body,
                           String sender, String orgId, String historyId) {
        List<List<String>> packedRecipients = partition(recipientsList,
            emailConnectorConfig.getMaxRecipientsPerEmail() - 1);

        for (int i = 0; i < packedRecipients.size(); i++) {
            Timer.Sample bopResponseTimeMetric = Timer.start(meterRegistry);
            bopManager.sendToBop(packedRecipients.get(i), subject, body, sender);
            bopResponseTimeMetric.stop(bopResponseTimer);
            Log.infof("Sent Email notification %d/%d [orgId=%s, historyId=%s]",
                i + 1, packedRecipients.size(), orgId, historyId);
        }
    }

    private static String requireEventDataField(EmailNotification emailNotification, String fieldName) {
        Object value = emailNotification.eventData().get(fieldName);
        if (value == null) {
            throw new IllegalStateException("Missing required field '" + fieldName + "' in event data");
        }
        return value.toString();
    }

    static List<List<String>> partition(Set<String> collection, int n) {
        AtomicInteger counter = new AtomicInteger();
        return collection.stream()
            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() / n))
            .values().stream().toList();
    }
}
