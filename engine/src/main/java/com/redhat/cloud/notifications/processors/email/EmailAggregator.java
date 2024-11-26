package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ExternalAuthorizationCriterion;
import com.redhat.cloud.notifications.processors.ExternalAuthorizationCriterionExtractor;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.ActionParsingException;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    EventTypeRepository eventTypeRepository;

    @Inject
    ActionParser actionParser;

    @Inject
    EngineConfig engineConfig;

    ConsoleCloudEventParser cloudEventParser = new ConsoleCloudEventParser();

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String RECIPIENTS_KEY = "recipients";

    @Inject
    ExternalAuthorizationCriterionExtractor externalAuthorizationCriteriaExtractor;

    @ConfigProperty(name = "notifications.aggregation.max-page-size", defaultValue = "100")
    int maxPageSize;

    public Map<User, Map<String, Object>> getAggregated(UUID appId, EmailAggregationKey aggregationKey, SubscriptionType subscriptionType, LocalDateTime start, LocalDateTime end) {

        Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        Map<String, Set<String>> subscribersByEventType = subscriptionRepository
                .getSubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);
        Map<String, Set<String>> unsubscribersByEventType = subscriptionRepository
                .getUnsubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);

        if (engineConfig.isAggregationBasedOnEventEnabled(aggregationKey.getOrgId()) && aggregationKey instanceof EventAggregationCriteria eventAggregationCriteria) {
            Log.infof("Processing aggregation for %s based on event table", aggregationKey.getOrgId());
            aggregationBasedOnEvent(eventAggregationCriteria, start, end, subscribersByEventType, unsubscribersByEventType, aggregated);
        } else {
            aggregationBasedOnEmailAggregation(aggregationKey, start, end, subscribersByEventType, unsubscribersByEventType, aggregated);
        }

        return aggregated.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(toMap(
                        Entry::getKey,
                        entry -> entry.getValue().getContext()
                ));
    }

    private void aggregationBasedOnEmailAggregation(EmailAggregationKey aggregationKey, LocalDateTime start, LocalDateTime end, Map<String, Set<String>> subscribersByEventType, Map<String, Set<String>> unsubscribersByEventType, Map<User, AbstractEmailPayloadAggregator> aggregated) {
        int offset = 0;
        int totalAggregatedElements = 0;

        List<EmailAggregation> aggregations;
        do {
            // First, we retrieve paginated aggregations that match the given key.
            aggregations = emailAggregationRepository.getEmailAggregation(aggregationKey, start, end, offset, maxPageSize);
            offset += maxPageSize;

            // For each aggregation...
            for (EmailAggregation aggregation : aggregations) {

                // We need its event type to determine the target endpoints.
                String eventTypeName = getEventType(aggregation);
                EventType eventType;
                try {
                    eventType = eventTypeRepository.getEventType(aggregationKey.getBundle(), aggregationKey.getApplication(), eventTypeName);
                } catch (NoResultException e) {
                    Log.warnf(e, "Unknown event type found in an aggregation payload [orgId=%s, bundle=%s, application=%s, eventType=%s]",
                            aggregationKey.getOrgId(), aggregationKey.getBundle(), aggregationKey.getApplication(), eventTypeName);
                    // The exception must not interrupt the loop.
                    continue;
                }
                // Let's retrieve these targets.
                Set<Endpoint> endpoints = Set.copyOf(endpointRepository
                    .getTargetEmailSubscriptionEndpoints(aggregationKey.getOrgId(), eventType.getId()));

                /*
                 * Now we want to determine who will actually receive the aggregation email.
                 * All users who subscribed to the current application and subscription type combination are recipients candidates.
                 * The actual recipients list may differ from the candidates depending on the endpoint properties and the action settings.
                 * The target endpoints properties will determine whether each candidate will actually receive an email.
                 */

                Set<String> subscribers = subscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());
                Set<String> unsubscribers = unsubscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());
                ExternalAuthorizationCriterion externalAuthorizationCriteria = externalAuthorizationCriteriaExtractor.extract(aggregation);

                Set<User> recipients = externalRecipientsResolver.recipientUsers(
                    aggregationKey.getOrgId(),
                    Stream.concat(
                        endpoints
                            .stream()
                            .map(EndpointRecipientSettings::new),
                        getActionRecipientSettings(aggregation.getPayload())
                    ).collect(toSet()),
                    subscribers,
                    unsubscribers,
                    eventType.isSubscribedByDefault(),
                    externalAuthorizationCriteria
                );

                /*
                 * We now have the final recipients list.
                 * Let's populate the Map that will be returned by the method.
                 */
                recipients.forEach(recipient -> {
                    // We may or may not have already initialized an aggregator for the recipient.
                    AbstractEmailPayloadAggregator aggregator = aggregated
                        .computeIfAbsent(recipient, ignored -> EmailPayloadAggregatorFactory.by(aggregationKey, start, end));
                    // It's aggregation time!
                    aggregator.aggregate(aggregation);
                });
            }
            totalAggregatedElements += aggregations.size();
        } while (maxPageSize == aggregations.size());
        Log.infof("%d elements were aggregated for key %s", totalAggregatedElements, aggregationKey);
    }

    private void aggregationBasedOnEvent(EventAggregationCriteria eventAggregationCriteria, LocalDateTime start, LocalDateTime end, Map<String, Set<String>> subscribersByEventType, Map<String, Set<String>> unsubscribersByEventType, Map<User, AbstractEmailPayloadAggregator> aggregated) {
        int offset = 0;
        int totalAggregatedElements = 0;

        List<Event> aggregations;
        do {
            // First, we retrieve paginated aggregations that match the given key.
            aggregations = emailAggregationRepository.getEmailAggregationBasedOnEvent(eventAggregationCriteria, start, end, offset, maxPageSize);
            offset += maxPageSize;

            // For each aggregation...
            for (Event aggregation : aggregations) {
                // We need its event type to determine the target endpoints.
                EventType eventType = aggregation.getEventType();

                // Let's retrieve these targets.
                Set<Endpoint> endpoints = Set.copyOf(endpointRepository
                    .getTargetEmailSubscriptionEndpoints(aggregation.getOrgId(), aggregation.getEventType().getId()));

                /*
                 * Now we want to determine who will actually receive the aggregation email.
                 * All users who subscribed to the current application and subscription type combination are recipients candidates.
                 * The actual recipients list may differ from the candidates depending on the endpoint properties and the action settings.
                 * The target endpoints properties will determine whether each candidate will actually receive an email.
                 */

                Set<String> subscribers = subscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());
                Set<String> unsubscribers = unsubscribersByEventType.getOrDefault(eventType.getName(), Collections.emptySet());
                aggregation.setEventWrapper(getEventWrapper(aggregation.getPayload()));
                ExternalAuthorizationCriterion externalAuthorizationCriterion = externalAuthorizationCriteriaExtractor.extract(aggregation);

                Log.info("Start calling external resolver service ");
                Set<User> recipients = externalRecipientsResolver.recipientUsers(
                    aggregation.getOrgId(),
                    Stream.concat(
                        endpoints
                            .stream()
                            .map(EndpointRecipientSettings::new),
                        getActionRecipientSettings(new JsonObject(aggregation.getPayload()))
                    ).collect(toSet()),
                    subscribers,
                    unsubscribers,
                    eventType.isSubscribedByDefault(),
                    externalAuthorizationCriterion
                );

                /*
                 * We now have the final recipients list.
                 * Let's populate the Map that will be returned by the method.
                 */
                recipients.forEach(recipient -> {
                    // We may or may not have already initialized an aggregator for the recipient.
                    AbstractEmailPayloadAggregator aggregator = aggregated
                        .computeIfAbsent(recipient, ignored -> EmailPayloadAggregatorFactory.by(eventAggregationCriteria, start, end));
                    // It's aggregation time!
                    EmailAggregation eventDataToAggregate = new EmailAggregation(aggregation.getOrgId(), eventAggregationCriteria.getBundle(), eventAggregationCriteria.getApplication(), new JsonObject(aggregation.getPayload()));
                    aggregator.aggregate(eventDataToAggregate);
                });
            }
            totalAggregatedElements += aggregations.size();
        } while (maxPageSize == aggregations.size());
        Log.infof("%d elements were aggregated for key %s", totalAggregatedElements, eventAggregationCriteria);
    }

    private EventWrapper<?, ?> getEventWrapper(String payload) {
        try {
            Action action = actionParser.fromJsonString(payload);
            return new EventWrapperAction(action);
        } catch (ActionParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                EventWrapperCloudEvent eventWrapperCloudEvent = new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload, NotificationsConsoleCloudEvent.class));
                return eventWrapperCloudEvent;
            } catch (ConsoleCloudEventParsingException cloudEventParseException) {
                /*
                 * An exception (most likely UncheckedIOException) was thrown during the payload parsing. The message
                 * is therefore considered rejected.
                 */

                actionParseException.addSuppressed(cloudEventParseException);
                throw actionParseException;
            }
        }
    }

    private String getEventType(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(EVENT_TYPE_KEY);
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
