package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class EmailAggregator {

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    FeatureFlipper featureFlipper;

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String RECIPIENTS_KEY = "recipients";

    @ConfigProperty(name = "notifications.get.aggregation.max.page.size", defaultValue = "10000")
    int aggregationMaxPageSize;

    private Set<String> getEmailSubscribers(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType) {
        return Set.copyOf(emailSubscriptionRepository
                .getEmailSubscribersUserId(aggregationKey.getOrgId(), aggregationKey.getBundle(), aggregationKey.getApplication(), null, emailSubscriptionType));
    }

    private Map<String, Set<String>> getEmailSubscribersGroupedByEventType(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType) {
        return emailSubscriptionRepository
            .getEmailSubscribersUserIdGroupedByEventType(aggregationKey.getOrgId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType);
    }

    private Set<String> getSubscribers(String eventType, Set<String> subscribers, Map<String, Set<String>> subscribersByEventType) {
        if (featureFlipper.isUseEventTypeForSubscriptionEnabled()) {
            if (subscribersByEventType.containsKey(eventType)) {
                return subscribersByEventType.get(eventType);
            } else {
                return Set.of();
            }
        }
        return subscribers;
    }

    public Map<User, Map<String, Object>> getAggregated(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType, LocalDateTime start, LocalDateTime end) {

        Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        Set<String> subscribers = null;
        Map<String, Set<String>> subscribersByEventType = null;

        if (featureFlipper.isUseEventTypeForSubscriptionEnabled()) {
            subscribersByEventType = getEmailSubscribersGroupedByEventType(aggregationKey, emailSubscriptionType);
        } else {
            subscribers = getEmailSubscribers(aggregationKey, emailSubscriptionType);
        }

        int offset = 0;
        int totalAggregatedElements = 0;

        List<EmailAggregation> aggregations;
        do {
            // First, we retrieve paginated aggregations that match the given key.
            aggregations = emailAggregationRepository.getEmailAggregation(aggregationKey, start, end, offset, aggregationMaxPageSize);
            offset += aggregationMaxPageSize;

            // For each aggregation...
            for (EmailAggregation aggregation : aggregations) {
                // We need its event type to determine the target endpoints.
                String eventType = getEventType(aggregation);
                // Let's retrieve these targets.
                Set<Endpoint> endpoints = Set.copyOf(endpointRepository
                    .getTargetEmailSubscriptionEndpoints(aggregationKey.getOrgId(), aggregationKey.getBundle(), aggregationKey.getApplication(), eventType));

                // Now we want to determine who will actually receive the aggregation email.
                // All users who subscribed to the current application and subscription type combination are recipients candidates.
                /*
                 * The actual recipients list may differ from the candidates depending on the endpoint properties and the action settings.
                 * The target endpoints properties will determine whether or not each candidate will actually receive an email.
                 */
                Set<User> users = recipientResolver.recipientUsers(
                    aggregationKey.getOrgId(),
                    Stream.concat(
                        endpoints
                            .stream()
                            .map(EndpointRecipientSettings::new),
                        getActionRecipient(aggregation).stream()
                    ).collect(Collectors.toSet()),
                    getSubscribers(eventType, subscribers, subscribersByEventType)
                );

                /*
                 * We now have the final recipients list.
                 * Let's populate the Map that will be returned by the method.
                 */
                users.forEach(user -> {
                    // It's aggregation time!
                    fillUsers(aggregationKey, user, aggregated, aggregation);
                });
            }
            totalAggregatedElements += aggregations.size();
        } while (aggregationMaxPageSize == aggregations.size());
        Log.infof("%d elements were aggregated for key %s", totalAggregatedElements, aggregationKey);

        return aggregated
                .entrySet()
                .stream()
                .peek(entry -> {
                    // TODO These fields could be passed to EmailPayloadAggregatorFactory.by since we know them from the beginning.
                    entry.getValue().setStartTime(start);
                    entry.getValue().setEndTimeKey(end);
                })
                .collect(
                        Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getContext()
                        )
                );
    }

    private void fillUsers(EmailAggregationKey aggregationKey, User user, Map<User, AbstractEmailPayloadAggregator> aggregated, EmailAggregation emailAggregation) {
        AbstractEmailPayloadAggregator aggregator = aggregated.computeIfAbsent(user, ignored -> EmailPayloadAggregatorFactory.by(aggregationKey));
        aggregator.aggregate(emailAggregation);
    }

    private String getEventType(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(EVENT_TYPE_KEY);
    }

    private List<ActionRecipientSettings> getActionRecipient(EmailAggregation emailAggregation) {
        if (emailAggregation.getPayload().containsKey(RECIPIENTS_KEY)) {
            JsonArray recipients = emailAggregation.getPayload().getJsonArray(RECIPIENTS_KEY);
            if (recipients.size() > 0) {
                return recipients.stream().map(r -> {
                    JsonObject recipient = (JsonObject) r;
                    return recipient.mapTo(Recipient.class);
                }).map(r -> new ActionRecipientSettings(r.getOnlyAdmins(), r.getIgnoreUserPreferences(), r.getUsers())).collect(Collectors.toList());
            }

        }
        return List.of();
    }

}
