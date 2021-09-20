package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.adapter.ActionRecipientAdapter;
import com.redhat.cloud.notifications.recipients.request.adapter.EndpointAdapter;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Inject
    ActionParser actionParser;

    private Uni<Set<String>> getEmailSubscribers(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType) {
        return subscriptionResources
                .getEmailSubscribersUserId(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType)
                .onItem().transform(Set::copyOf)
                // This will prevent multiple database queries executions during the aggregation process.
                .memoize().indefinitely();
    }

    public Uni<Map<User, Map<String, Object>>> getAggregated(EmailAggregationKey aggregationKey, EmailSubscriptionType emailSubscriptionType, LocalDateTime start, LocalDateTime end) {
        Map<User, AbstractEmailPayloadAggregator> aggregated = new HashMap<>();
        Uni<Set<String>> subscribers = getEmailSubscribers(aggregationKey, emailSubscriptionType);
        // First, we retrieve all aggregations that match the given key.
        return emailAggregationResources.getEmailAggregation(aggregationKey, start, end)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                // For each aggregation...
                .onItem().transformToUniAndConcatenate(aggregation -> {
                    // We need its event type to determine the target endpoints.
                    return actionParser.fromJsonString(aggregation.getPayload().encode())
                            .onItem().transformToUni(action -> {
                                // Let's retrieve these targets.
                                return endpointResources.getTargetEndpointsFromType(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), action.getEventType(), EndpointType.EMAIL_SUBSCRIPTION)
                                        .onItem().transform(Set::copyOf)
                                        // Now we want to determine who will actually receive the aggregation email.
                                        .onItem().transformToUni(endpoints ->
                                                // All users who subscribed to the current application and subscription type combination are recipients candidates.
                                                subscribers.onItem().transformToUni(users ->
                                                        /*
                                                         * The actual recipients list is a subset of the candidates.
                                                         * The target endpoints properties will determine whether or not each candidate will actually receive an email.
                                                         */
                                                        recipientResolver.recipientUsers(
                                                                aggregationKey.getAccountId(),
                                                                Stream.concat(
                                                                        endpoints
                                                                                .stream()
                                                                                .map(EndpointAdapter::new),
                                                                        ActionRecipientAdapter.fromAction(action).stream()
                                                                ).collect(Collectors.toSet()),
                                                                users
                                                        )
                                                )
                                        )
                                        /*
                                         * We now have the final recipients list.
                                         * Let's populate with synchronized side-effects (invoke) the Map that will be returned by the method.
                                         */
                                        .onItem().invoke(users -> {
                                            users.forEach(user -> {
                                                // It's aggregation time!
                                                fillUsers(aggregationKey, user, aggregated, aggregation);
                                            });
                                        });
                            });
                })
                // We need to wait for everything to be done before proceeding to the final step.
                .onItem().ignoreAsUni()
                // We need to return the Map that was populated using synchronized side-effects.
                .replaceWith(aggregated)
                .onItem().transform(aggregatorMap ->
                        aggregatorMap
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
                                )
                );
    }

    private void fillUsers(EmailAggregationKey aggregationKey, User user, Map<User, AbstractEmailPayloadAggregator> aggregated, EmailAggregation emailAggregation) {
        AbstractEmailPayloadAggregator aggregator = aggregated.computeIfAbsent(user, ignored -> EmailPayloadAggregatorFactory.by(aggregationKey));
        aggregator.aggregate(emailAggregation);
    }

}
