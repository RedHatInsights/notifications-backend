package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.webhooks.WebhookProcessor;
import com.redhat.cloud.notifications.webhooks.transformers.PoliciesTransformer;
import io.smallrye.mutiny.Uni;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    WebhookProcessor webhooks;

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
                .onItem().transformToUni(notif -> webhooks.process(notif));
    }

    public Uni<Object> transform(Action action) {
        // Transform to destination format, based on the input (sender) and output (processor type)
        return transformer.transform(action); // We only have a single type right now
    }
}
