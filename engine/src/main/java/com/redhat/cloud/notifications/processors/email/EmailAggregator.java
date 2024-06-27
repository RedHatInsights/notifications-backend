package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ExternalAuthorizationCriteria;
import com.redhat.cloud.notifications.processors.ExternalAuthorizationCriteriaExtractor;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
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
    RecipientResolver recipientResolver;

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    EngineConfig engineConfig;

    @Inject
    EventTypeRepository eventTypeRepository;

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String RECIPIENTS_KEY = "recipients";

    @Inject
    ExternalAuthorizationCriteriaExtractor externalAuthorizationCriteriaExtractor;

    @ConfigProperty(name = "notifications.aggregation.max-page-size", defaultValue = "100")
    int maxPageSize;

    public Map<User, Map<String, Object>> getAggregated(UUID appId, EmailAggregationKey aggregationKey, SubscriptionType subscriptionType, LocalDateTime start, LocalDateTime end) {

        Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        Map<String, Set<String>> subscribersByEventType = subscriptionRepository
                .getSubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);
        Map<String, Set<String>> unsubscribersByEventType = subscriptionRepository
                .getUnsubscribersByEventType(aggregationKey.getOrgId(), appId, subscriptionType);

        int offset = 0;
        int totalAggregatedElements = 0;

        List<EmailAggregation> aggregations;
        do {
            // First, we retrieve paginated aggregations that match the given key.
            aggregations = emailAggregationRepository.getEmailAggregation(aggregationKey, start, end, offset, maxPageSize);
            offset += maxPageSize;

            // For each aggregation...
            for (EmailAggregation aggregation : aggregations) {

                ExternalAuthorizationCriteria externalAuthorizationCriteria = externalAuthorizationCriteriaExtractor.extract(aggregation);
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

                Set<User> recipients;
                if (engineConfig.isAggregationWithRecipientsResolverEnabled()) {
                    try {
                        Log.info("Start calling external resolver service ");
                        recipients = externalRecipientsResolver.recipientUsers(
                            aggregationKey.getOrgId(),
                            Stream.concat(
                                endpoints
                                    .stream()
                                    .map(EndpointRecipientSettings::new),
                                getActionRecipientSettings(aggregation)
                            ).collect(toSet()),
                            subscribers,
                            unsubscribers,
                            eventType.isSubscribedByDefault(),
                            externalAuthorizationCriteria
                        );
                    } catch (Exception ex) {
                        Log.error("Error calling external recipients resolver service", ex);
                        recipients = getRecipients(aggregationKey, subscribers, getActionRecipientSettings(aggregation), endpoints);
                    }
                } else {
                    recipients = getRecipients(aggregationKey, subscribers, getActionRecipientSettings(aggregation), endpoints);
                }

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

        return aggregated.entrySet().stream()
                .collect(toMap(
                        Entry::getKey,
                        entry -> entry.getValue().getContext()
                ));
    }

    @Deprecated(forRemoval = true)
    private Set<User> getRecipients(EmailAggregationKey aggregationKey, Set<String> subscribers, Stream<ActionRecipientSettings> actionRecipientSettings, Set<Endpoint> endpoints) {
        return recipientResolver.recipientUsers(
            aggregationKey.getOrgId(),
            Stream.concat(
                endpoints
                    .stream()
                    .map(EndpointRecipientSettings::new),
                    actionRecipientSettings
            ).collect(toSet()),
            subscribers
        );
    }

    private String getEventType(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(EVENT_TYPE_KEY);
    }

    private Stream<ActionRecipientSettings> getActionRecipientSettings(EmailAggregation emailAggregation) {
        if (emailAggregation.getPayload().containsKey(RECIPIENTS_KEY)) {
            JsonArray recipients = emailAggregation.getPayload().getJsonArray(RECIPIENTS_KEY);
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
