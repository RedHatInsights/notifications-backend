package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.webhooks.WebhookProcessor;
import com.redhat.cloud.notifications.webhooks.transformers.PoliciesTransformer;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    WebhookProcessor webhooks;

    @Inject
    Vertx vertx;

    @Inject
    PoliciesTransformer transformer;

    public Uni<Void> process(Action action) {
        /*
        TODO For the first iteration, this won't do anything useful (just a small transform of data)
        For later versions, the process should be:

            - This will modify the action to proper notifications matching the target rules (action sets etc)
            - The processor will only get the more detailed information it needs based on the type it wants
            - So fetch endpoints here (without JOINs) and fetch properties in the processor
         */
        return Uni.createFrom().item(action)
                .onItem().transformToUni(this::transform)
                .onItem().transform(payload -> new Notification(action.getTenantId(), payload))
                .onItem().transformToUni(notification -> {
                    // TODO Get endpoints here, send each endpointType to the correct processor
                    String addr = String.format("notifications-%s", notification.getTenant());
                    Uni<Void> webhooksResult = webhooks.process(notification);
                    // TODO Investigate the performance of this write.. is it blocking or not?
                    //      does it need to be closed?
                    //      This should also be a separate processor
                    Uni<Void> writeToBus = vertx.eventBus().publisher(addr).write(notification);
                    return Uni.combine().all().unis(webhooksResult, writeToBus).discardItems();
                });
    }

    public Uni<Object> transform(Action action) {
        // Transform to destination format, based on the input (sender) and output (processor type)
        return transformer.transform(action); // We only have a single type right now
    }
}
