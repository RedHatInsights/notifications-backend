package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResourcesJDBC;
import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Multi;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    EndpointResourcesJDBC resources;

    public Multi<Notification> process(Action action) {
        // Take input action and replace it with the correct endpoints

        // Fetch all the endpoints
        // This should filter for action sets etc when necessary - for now we fetch all the tenant targets
        return resources
                // TODO This doesn't make sense (webhooks processor fetches all the same info as well)
                .getEndpoints(action.getTenantId())
                // Map to new entity with all the necessary information for invocation
                .onItem().apply(endpoint -> new Notification(action.getTenantId(), action.getEvent()));
    }
}
