package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor;
import com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointProcessor {

    public static final String PROCESSED_MESSAGES_COUNTER_NAME = "processor.input.processed";
    public static final String PROCESSED_ENDPOINTS_COUNTER_NAME = "processor.input.endpoint.processed";

    private static final Logger LOGGER = Logger.getLogger(EndpointProcessor.class);

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    WebhookTypeProcessor webhooks;

    @Inject
    CamelTypeProcessor camel;

    @Inject
    EmailSubscriptionTypeProcessor emails;

    @Inject
    MeterRegistry registry;

    private Counter processedItems;
    private Counter endpointTargeted;

    @PostConstruct
    void init() {
        processedItems = registry.counter(PROCESSED_MESSAGES_COUNTER_NAME);
        endpointTargeted = registry.counter(PROCESSED_ENDPOINTS_COUNTER_NAME);
    }

    public Uni<Void> process(Event event) {
        processedItems.increment();
        return endpointRepository.getTargetEndpoints(event.getAccountId(), event.getEventType())

                // Target endpoints are grouped by endpoint type.
                .onItem().transformToMulti(endpoints -> {
                    endpointTargeted.increment(endpoints.size());
                    Map<EndpointType, List<Endpoint>> endpointsByType = endpoints.stream().collect(Collectors.groupingBy(Endpoint::getType));
                    return Multi.createFrom().iterable(endpointsByType.entrySet());
                })

                /*
                 * For each endpoint type, the list of target endpoints is sent alongside with the action to the relevant processor.
                 * Each processor returns a list of history entries. All of the returned lists are flattened into a single list.
                 */
                .onItem().transformToMultiAndConcatenate(entry -> {
                    EndpointTypeProcessor processor = endpointTypeToProcessor(entry.getKey());
                    return processor.process(event, entry.getValue());
                })

                // TODO Action processing and history persistence should be a single atomic operation.
                // Now each history entry is persisted.
                .onItem().transformToUniAndConcatenate(history -> notificationHistoryRepository.createNotificationHistory(history)
                        .onFailure().invoke(failure -> LOGGER.errorf("Notification history creation failed for %s", history.getEndpoint()))
                )
                .onItem().ignoreAsUni();
    }

    private EndpointTypeProcessor endpointTypeToProcessor(EndpointType endpointType) {
        switch (endpointType) {
            case CAMEL:
                return camel;
            case WEBHOOK:
                return webhooks;
            case EMAIL_SUBSCRIPTION:
                return emails;
            default:
                return (action, endpoints) -> Multi.createFrom().empty();
        }
    }
}
