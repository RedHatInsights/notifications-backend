package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor;
import com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.Function;
import java.util.logging.Logger;

@ApplicationScoped
public class EndpointProcessor {

    public static final String PROCESSED_MESSAGES_COUNTER_NAME = "processor.input.processed";
    public static final String PROCESSED_ENDPOINTS_COUNTER_NAME = "processor.input.endpoint.processed";

    private static final Logger LOGGER = Logger.getLogger(EndpointProcessor.class.getName());

    @Inject
    Mutiny.Session session;

    @Inject
    EndpointResources resources;

    // TODO [BG Phase 2] Delete this attribute
    @Inject
    DefaultProcessor defaultProcessor;

    @Inject
    NotificationResources notifResources;

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

    public Uni<Void> process(Action action) {
        processedItems.increment();
        // TODO [BG Phase 2] Use EndpointResources.getTargetEndpoints here
        return getEndpoints(
                action.getAccountId(),
                action.getBundle(),
                action.getApplication(),
                action.getEventType())
                .onItem().transformToUniAndConcatenate(endpoint -> {
                    LOGGER.fine(() -> "Sending notification to " + endpoint);
                    endpointTargeted.increment();
                    Notification endpointNotif = new Notification(action, endpoint);
                    return endpointTypeToProcessor(endpoint.getType()).process(endpointNotif);
                })
                .onItem().transformToUniAndConcatenate(history -> notifResources.createNotificationHistory(history)
                        .onFailure().invoke(failure -> LOGGER.severe("Notification history creation failed for " + history.getEndpoint()))
                )
                .onItem().ignoreAsUni()
                .onItemOrFailure().call(() -> Uni.createFrom().item(() -> session.clear()));
    }

    public EndpointTypeProcessor endpointTypeToProcessor(EndpointType endpointType) {
        switch (endpointType) {
            case CAMEL:
                return camel;
            case WEBHOOK:
                return webhooks;
            case EMAIL_SUBSCRIPTION:
                return emails;
            default:
                return notification -> Uni.createFrom().nullItem();
        }
    }

    // TODO [BG Phase 2] Delete this method
    public Multi<Endpoint> getEndpoints(String tenant, String bundleName, String applicationName, String eventTypeName) {
        return resources.getTargetEndpoints(tenant, bundleName, applicationName, eventTypeName)
                .onItem().transformToMultiAndConcatenate((Function<Endpoint, Multi<Endpoint>>) endpoint -> {
                    // If the tenant has a default endpoint for the eventType, then add the target endpoints here
                    if (endpoint.getType() == EndpointType.DEFAULT) {
                        return defaultProcessor.getDefaultEndpoints(endpoint);
                    }
                    return Multi.createFrom().item(endpoint);
                });
    }
}
