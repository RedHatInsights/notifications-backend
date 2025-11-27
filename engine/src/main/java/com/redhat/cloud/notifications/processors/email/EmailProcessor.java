package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import com.redhat.cloud.notifications.utils.RecipientsAuthorizationCriterionExtractor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;

@ApplicationScoped
public class EmailProcessor extends SystemEndpointTypeProcessor {
    @Inject
    ConnectorSender connectorSender;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Inject
    EmailPendoResolver emailPendoResolver;

    @Inject
    TemplateService quteTemplateService;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    @Inject
    Environment environment;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EngineConfig engineConfig;

    @Override
    public void process(final Event event, final List<Endpoint> endpoints) {

        final TemplateDefinition bodyTemplateDefinition = new TemplateDefinition(
            IntegrationType.EMAIL_BODY,
            event.getEventType().getApplication().getBundle().getName(),
            event.getEventType().getApplication().getName(),
            event.getEventType().getName());

        final TemplateDefinition subjectTemplateDefinition = new TemplateDefinition(
            IntegrationType.EMAIL_TITLE,
            event.getEventType().getApplication().getBundle().getName(),
            event.getEventType().getApplication().getName(),
            event.getEventType().getName());

        if (!quteTemplateService.isValidTemplateDefinition(subjectTemplateDefinition) ||
            !quteTemplateService.isValidTemplateDefinition(bodyTemplateDefinition)) {
            Log.infof("[event_uuid: %s] The event was skipped because there were no suitable templates for its event type %s", event.getId(), event.getEventType().getName());
            return;
        }

        Set<String> subscribers;
        Set<String> unsubscribers;

        Optional<Severity> eventSeverity = Optional.empty();
        if (engineConfig.isIncludeSeverityToFilterRecipientsEnabled(event.getOrgId())) {
            eventSeverity = Optional.ofNullable(event.getSeverity());
        }

        if (event.getEventType().isSubscribedByDefault()) {
            subscribers = Collections.emptySet();
            unsubscribers = Set.copyOf(subscriptionRepository.getUnsubscribers(event.getOrgId(), event.getEventType().getId(), INSTANT, eventSeverity));
        } else {
            subscribers = Set.copyOf(subscriptionRepository.getSubscribers(event.getOrgId(), event.getEventType().getId(), INSTANT, eventSeverity));
            unsubscribers = Collections.emptySet();
        }

        final Set<RecipientSettings> recipientSettings = this.extractAndTransformRecipientSettings(event, endpoints);

        /*
         * When:
         * - the event type is NOT subscribed by default
         * - the user preferences are NOT ignored
         * - there are no subscribers to the event type
         * Then, there's no need to further process the recipient settings because no email will be sent from them.
         */
        recipientSettings.removeIf(settings -> !event.getEventType().isSubscribedByDefault() && !settings.isIgnoreUserPreferences() && subscribers.isEmpty());

        // If we removed all recipient settings, it means no email will be sent from the event and we can exit this method.
        if (recipientSettings.isEmpty()) {
            Log.debugf("[event_uuid: %s] The event was skipped because there were no subscribers for it and user preferences are not ignored", event.getId());
            return;
        }

        final boolean ignoreUserPreferences = recipientSettings.stream().filter(RecipientSettings::isIgnoreUserPreferences).count() > 0;

        // we don't want to include outage pendo message this time
        EmailPendo pendoMessage = emailPendoResolver.getPendoEmailMessage(event, ignoreUserPreferences, false);

        Map<String, Object> eventData = convertEventAsDataMap(event, pendoMessage, ignoreUserPreferences);

        // Prepare all the data to be sent to the connector.
        final EmailNotification emailNotification = new EmailNotification(
            emailActorsResolver.getEmailSender(event),
            event.getOrgId(),
            recipientSettings,
            subscribers,
            unsubscribers,
            event.getEventType().isSubscribedByDefault(),
            recipientsAuthorizationCriterionExtractor.extract(event),
            eventData,
            false
        );

        Log.debugf("[org_id: %s] Sending email notification to connector", emailNotification);

        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EMAIL_SUBSCRIPTION);

        connectorSender.send(event, endpoint, payload);
    }

    protected Map<String, Object> convertEventAsDataMap(Event event, EmailPendo pendoMessage, boolean ignoreUserPreferences) {
        JsonObject data = baseTransformer.toJsonObject(event);
        data.put("environment", JsonObject.mapFrom(environment));
        data.put("pendo_message", pendoMessage);
        data.put("ignore_user_preferences", ignoreUserPreferences);
        data.put("orgId", event.getOrgId());

        Map<String, Object> dataAsMap;
        try {
            dataAsMap = objectMapper.readValue(data.encode(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Instant email notification data transformation failed", e);
        }
        return dataAsMap;
    }
}
