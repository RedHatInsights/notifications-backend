package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.EventBusTypeProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.reactivestreams.Publisher;

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
    WebhookTypeProcessor webhooks;

    public Uni<Void> process(Action action) {
        // TODO ApplicationName and eventType are going to be extracted from the new input model - from another PR
        //      We also need to add the original message's unique id to the notification

        Uni<Void> endpointsCallResult = getEndpoints(action.getEvent().getAccountId(), "Policies", "All")
                .onItem()
                .transformToUni(endpoint -> {
                    Notification endpointNotif = new Notification(action, endpoint);
                    return endpointTypeToProcessor(endpoint.getType()).process(endpointNotif);
                })
                .merge()
                .onItem()
                .ignoreAsUni();

        // Notification is an endpoint type as well? Must it be created manually each time?
        Notification notification = new Notification(action, null);
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

    public Multi<Endpoint> getEndpoints(String tenant, String applicationName, String eventTypeName) {
        return resources.getTargetEndpoints(tenant, applicationName, eventTypeName)
                .flatMap((Function<Endpoint, Publisher<Endpoint>>) endpoint -> {
                    // If the tenant has a default endpoint for the eventType, then add the target endpoints here
                    if (endpoint.getType() == Endpoint.EndpointType.DEFAULT) {
                        return defaultProcessor.getDefaultEndpoints(endpoint);
                    }
                    return Multi.createFrom().item(endpoint);
                });
    }
}
