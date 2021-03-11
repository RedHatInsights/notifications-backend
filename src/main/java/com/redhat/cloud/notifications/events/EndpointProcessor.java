package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.NotificationResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.EventBusTypeProcessor;
import com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.reactivestreams.Publisher;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.Function;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    EndpointResources resources;

    @Inject
    EventBusTypeProcessor notificationProcessor;

    @Inject
    DefaultProcessor defaultProcessor;

    @Inject
    NotificationResources notifResources;

    @Inject
    WebhookTypeProcessor webhooks;

    @Inject
    EmailSubscriptionTypeProcessor emails;

    @Inject
    MeterRegistry registry;

    private Counter processedItems;
    private Counter endpointTargeted;

    @PostConstruct
    void init() {
        processedItems = registry.counter("processor.input.processed");
        endpointTargeted = registry.counter("processor.input.endpoint.processed");
    }

    public Uni<Void> process(Action action) {
        processedItems.increment();
        Multi<NotificationHistory> endpointsCallResult = getEndpoints(
                action.getAccountId(),
                action.getBundle(),
                action.getApplication(),
                action.getEventType())
                .onItem()
                .transformToUni(endpoint -> {
                    endpointTargeted.increment();
                    Notification endpointNotif = new Notification(action, endpoint);
                    return endpointTypeToProcessor(endpoint.getType()).process(endpointNotif);
                })
                .concatenate()
                .onItem().transformToUni(history -> notifResources.createNotificationHistory(history))
                .concatenate();

        // Should this be a separate endpoint type as well (since it is configurable) ?
        Notification notification = new Notification(action, null);
        Uni<NotificationHistory> notificationResult = notificationProcessor.process(notification);

        return endpointsCallResult
                .onItem().ignoreAsUni()
                .replaceWith(notificationResult)
                .replaceWith(Uni.createFrom().voidItem());
    }

    public EndpointTypeProcessor endpointTypeToProcessor(Endpoint.EndpointType endpointType) {
        switch (endpointType) {
            case WEBHOOK:
                return webhooks;
            case EMAIL_SUBSCRIPTION:
                return emails;
            default:
                return notificationProcessor;
        }
    }

    public Multi<Endpoint> getEndpoints(String tenant, String bundleName, String applicationName, String eventTypeName) {
        return resources.getTargetEndpoints(tenant, bundleName, applicationName, eventTypeName)
                .flatMap((Function<Endpoint, Publisher<Endpoint>>) endpoint -> {
                    // If the tenant has a default endpoint for the eventType, then add the target endpoints here
                    if (endpoint.getType() == Endpoint.EndpointType.DEFAULT) {
                        return defaultProcessor.getDefaultEndpoints(endpoint);
                    }
                    return Multi.createFrom().item(endpoint);
                });
    }
}
