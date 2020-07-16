package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Multi;
import org.hawkular.alerts.api.model.action.Action;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EndpointProcessor {

    @Inject
    EndpointResources resources;

    public Multi<Notification> process(Action action) {
        // Take input action and replace it with the correct endpoints

        // Fetch all the endpoints
        // This should filter for action sets etc when necessary - for now we fetch all the tenant targets
        return resources
                .getEndpoints(action.getTenantId())
                // Map to new entity with all the necessary information for invocation
                .onItem().apply(endpoint -> new Notification(action.getTenantId(), endpoint.getId().toString(), action.getEvent()));
    }
}
