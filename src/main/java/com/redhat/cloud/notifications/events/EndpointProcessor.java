package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.EventBusTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.smallrye.mutiny.Uni;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    EndpointResources resources;

    @Inject
    EventBusTypeProcessor notificationProcessor;

    @Inject
    WebhookTypeProcessor webhooks;

    public Uni<Void> process(Action action) {
        // TODO These are going to be extracted from the new input model - from another PR
        //      We also need to add the original message's unique id to the notification
        Uni<Void> endpointsCallResult = resources.getTargetEndpoints(action.getTenantId(), "Policies", "All")
                .onItem()
                .transformToUni(endpoint -> {
                    Notification endpointNotif = new Notification(action.getTenantId(), action, endpoint);
                    return endpointTypeToProcessor(endpoint.getType()).process(endpointNotif);
                })
                .merge()
                .onItem()
                .ignoreAsUni();

        Notification notification = new Notification(action.getTenantId(), action, null);
        Uni<Void> notificationResult = notificationProcessor.process(notification);

        return Uni.combine().all().unis(endpointsCallResult, notificationResult).discardItems();
    }

    public EndpointTypeProcessor endpointTypeToProcessor(Endpoint.EndpointType endpointType) {
        switch (endpointType) {
            case WEBHOOK:
                return webhooks;
            default:
                return notificationProcessor;
        }
    }
}
