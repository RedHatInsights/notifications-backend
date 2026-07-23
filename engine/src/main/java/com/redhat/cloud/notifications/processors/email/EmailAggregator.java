package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import com.redhat.cloud.notifications.utils.RecipientsAuthorizationCriterionExtractor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class EmailAggregator {

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    EngineConfig engineConfig;

    @Inject
    BaseTransformer baseTransformer;

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String RECIPIENTS_KEY = "recipients";

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    @ConfigProperty(name = "notifications.aggregation.max-page-size", defaultValue = "100")
    int maxPageSize;

    public Map<User, Map<String, Object>> getAggregated(UUID appId, EventAggregationCriterion aggregationKey, SubscriptionType subscriptionType, LocalDateTime start, LocalDateTime end) {

        Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        Map<String, Set<String>> subscribersByEventType = subscriptionRepository
                .getSubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);
        Map<String, Set<String>> unsubscribersByEventType = subscriptionRepository
                .getUnsubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);

        Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities = Optional.empty();
        if (engineConfig.isIncludeSeverityToFilterRecipientsEnabled(aggregationKey.getOrgId())) {
            subscribersWithSeverities = Optional.of(subscriptionRepository.getSubscriptionsByEventTypeWithSeverities(aggregationKey.getOrgId(), appId, subscriptionType));
        }

        aggregationBasedOnEvent(aggregationKey, start, end, subscribersByEventType, unsubscribersByEventType, subscribersWithSeverities, aggregated);

        return aggregated.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(toMap(
                        Entry::getKey,
                        entry -> entry.getValue().getContext()
                ));
    }

    private void aggregationBasedOnEvent(EventAggregationCriterion eventAggregationCriteria,
                                         LocalDateTime start,
                                         LocalDateTime end,
                                         Map<String, Set<String>> subscribersByEventType,
                                         Map<String, Set<String>> unsubscribersByEventType,
                                         Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities,
                                         Map<User, AbstractEmailPayloadAggregator> aggregated) {
        int offset = 0;
        int totalAggregatedElements = 0;

        List<Event> aggregations;
        do {
            // Retrieve paginated aggregations that match the given key.
            aggregations = emailAggregationRepository.getEmailAggregationBasedOnEvent(eventAggregationCriteria, start, end, offset, maxPageSize);
            offset += maxPageSize;

            for (Event aggregation : aggregations) {
                Set<User> recipients = resolveRecipients(aggregation, subscribersByEventType, unsubscribersByEventType);
                aggregateForRecipients(recipients, aggregation, eventAggregationCriteria, subscribersWithSeverities, aggregated);
            }
            totalAggregatedElements += aggregations.size();
        } while (maxPageSize == aggregations.size());
        Log.infof("%d elements were aggregated for key %s", totalAggregatedElements, eventAggregationCriteria);
    }

    /*
     * Determines who will actually receive the aggregation email for a single event.
     * All users who subscribed to the current application and subscription type combination are recipient candidates.
     * The actual recipients list may differ from the candidates depending on the endpoint properties and the action settings.
     * The target endpoint properties will determine whether each candidate will actually receive an email.
     */
    private Set<User> resolveRecipients(Event aggregation,
                                        Map<String, Set<String>> subscribersByEventType,
                                        Map<String, Set<String>> unsubscribersByEventType) {
        EventType eventType = aggregation.getEventType();

        Set<Endpoint> endpoints = Set.copyOf(endpointRepository
            .getTargetEmailSubscriptionEndpoints(aggregation.getOrgId(), eventType.getId()));

        Set<String> subscribers = subscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());
        Set<String> unsubscribers = unsubscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());

        // extract() sets the event wrapper on the aggregation if not already set
        RecipientsAuthorizationCriterion externalAuthorizationCriterion = recipientsAuthorizationCriterionExtractor.extract(aggregation);

        return externalRecipientsResolver.recipientUsers(
            aggregation.getOrgId(),
            Stream.concat(
                endpoints.stream().map(EndpointRecipientSettings::new),
                getActionRecipientSettings(new JsonObject(aggregation.getPayload()))
            ).collect(toSet()),
            subscribers,
            unsubscribers,
            eventType.isSubscribedByDefault(),
            externalAuthorizationCriterion
        ).stream().filter(user -> user.getEmail() != null && !user.getEmail().isBlank()).collect(toSet());
    }

    /*
     * For each recipient, creates or retrieves an existing aggregator and feeds the event into it.
     * A single aggregator instance is shared across all events for a given user, accumulating data
     * throughout the aggregation window.
     */
    private void aggregateForRecipients(Set<User> recipients,
                                        Event aggregation,
                                        EventAggregationCriterion eventAggregationCriteria,
                                        Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities,
                                        Map<User, AbstractEmailPayloadAggregator> aggregated) {
        recipients.forEach(recipient -> {
            Set<SubscribedEventTypeSeverities> userSubscribedSeverities = subscribersWithSeverities
                .map(map -> map.get(recipient.getUsername()))
                .orElse(null);

            // We may or may not have already initialized an aggregator for the recipient.
            AbstractEmailPayloadAggregator aggregator = aggregated.computeIfAbsent(
                recipient,
                notUsed -> EmailPayloadAggregatorFactory.by(eventAggregationCriteria, recipient.getUsername(), userSubscribedSeverities));

            if (aggregator == null) {
                Log.warnf("No aggregator found for %s/%s, skipping recipient %s",
                    eventAggregationCriteria.getBundle(), eventAggregationCriteria.getApplication(), recipient.getUsername());
                return;
            }

            EmailAggregation eventDataToAggregate = new EmailAggregation(
                aggregation.getOrgId(),
                eventAggregationCriteria.getBundle(),
                eventAggregationCriteria.getApplication(),
                baseTransformer.toJsonObject(aggregation),
                aggregation.getSeverity(),
                aggregation.getEventType().getId());
            aggregator.aggregate(eventDataToAggregate);
        });
    }

    private Stream<ActionRecipientSettings> getActionRecipientSettings(JsonObject payload) {
        if (payload.containsKey(RECIPIENTS_KEY)) {
            JsonArray recipients = payload.getJsonArray(RECIPIENTS_KEY);
            if (!recipients.isEmpty()) {
                return recipients.stream().map(r -> {
                    JsonObject recipient = (JsonObject) r;
                    return recipient.mapTo(Recipient.class);
                }).map(r -> new ActionRecipientSettings(r.getOnlyAdmins(), r.getIgnoreUserPreferences(), r.getUsers(), r.getEmails()));
            }
        }
        return Stream.empty();
    }
}
