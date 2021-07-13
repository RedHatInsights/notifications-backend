package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.User;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailAggregator {

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    // This is manually used from the JSON payload instead of converting it to an Action and using getEventType()
    private static final String EVENT_TYPE_KEY = "event_type";

    public Uni<Map<User, Map<String, Object>>> getAggregated(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType, LocalDateTime start, LocalDateTime end) {
        Uni<List<EmailAggregation>> emailAggregationsUni = emailAggregationResources.getEmailAggregation(aggregationKey, start, end);

        Multi<Tuple2<String, List<Endpoint>>> endpointsPerEventTypeMulti = emailAggregationsUni
                .onItem().transform(emailAggregations -> emailAggregations.stream()

                        .map(this::getEventType)
                        .distinct()
                        .collect(Collectors.toList())
                ).onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToUniAndConcatenate(eventType -> Uni.combine().all().unis(
                        Uni.createFrom().item(eventType),
                        endpointResources.getTargetEndpointsFromType(
                                aggregationKey.getAccountId(),
                                aggregationKey.getBundle(),
                                aggregationKey.getApplication(),
                                eventType,
                                EndpointType.EMAIL_SUBSCRIPTION
                        )
                ).asTuple());

        Uni<Map<String, List<User>>> subscribersByEventTypeUni = subscriptionResources
                .getEmailSubscribers(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType)
                .onItem().transformToMulti(emailSubscriptions -> {
                    Set<String> subscribers = emailSubscriptions.stream().map(EmailSubscription::getUserId).collect(Collectors.toSet());

                    return endpointsPerEventTypeMulti
                            .onItem().transformToUniAndConcatenate(
                                endpointsPerEventType -> Uni.combine().all().unis(
                                        Uni.createFrom().item(endpointsPerEventType.getItem1()),
                                        recipientResolver.recipientUsers(aggregationKey.getAccountId(), endpointsPerEventType.getItem2(), subscribers)
                                ).asTuple()
                            );
                }).collect().asMap(Tuple2::getItem1, Tuple2::getItem2);

        return subscribersByEventTypeUni.onItem().transformToUni(subscribersByEventType ->
                emailAggregationsUni.onItem().transform(emailAggregations -> {
                    Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();

                    for (EmailAggregation emailAggregation : emailAggregations) {
                        subscribersByEventType
                                .get(getEventType(emailAggregation))
                                .forEach(user -> fillUsers(aggregationKey, user, aggregated, emailAggregation));
                    }

                    return aggregated;
                })
        ).onItem().transform(aggregatorMap -> aggregatorMap
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().getProcessedAggregations() > 0)
                .collect(
                    Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getContext()
                    )
                )
        );
    }

    private void fillUsers(EmailAggregationKey aggregationKey, User user, Map<User, AbstractEmailPayloadAggregator> aggregated, EmailAggregation emailAggregation) {
        if (!aggregated.containsKey(user)) {
            aggregated.put(user, EmailPayloadAggregatorFactory.by(aggregationKey));
        }

        AbstractEmailPayloadAggregator aggregator = aggregated.get(user);
        aggregator.aggregate(emailAggregation);
    }

    private String getEventType(EmailAggregation aggregation) {
        return aggregation.getPayload().getString(EVENT_TYPE_KEY);
    }

}
