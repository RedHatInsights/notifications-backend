package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Multi;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EndpointProcessor {

    public Multi<Notification> process(Action action) {
        /*
        TODO For the first iteration, this won't do anything useful (just a small transform of data)
        For later versions, the process should be:

            - This will modify the action to proper notifications matching the target rules (action sets etc)
            - The processor will only get the more detailed information it needs based on the type it wants
            - So fetch endpoints here (without JOINs) and fetch properties in the processor
         */
        return Multi.createFrom().item(action)
                .onItem().transform(endpoint -> new Notification(action.getTenantId(), action.getEvent()));
    }
}
