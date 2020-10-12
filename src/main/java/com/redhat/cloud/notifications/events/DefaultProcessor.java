package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.DefaultsAttributes;
import com.redhat.cloud.notifications.models.Endpoint;
import io.smallrye.mutiny.Multi;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class DefaultProcessor {
    @Inject
    EndpointResources resources;

    public Multi<Endpoint> getDefaultEndpoints(Endpoint defaultEndpoint) {
        // If the tenant has a default setting for the eventType, then replace the output Endpoints with this
        // If you want to modify the behavior to make these defaults as addon to the other configured ones, replace the behavior in
        // EndpointProcessor
        DefaultsAttributes defaultsAttributes = (DefaultsAttributes) defaultEndpoint.getProperties();
        return Multi.createFrom().iterable(defaultsAttributes.getTargetEndpoints())
                .onItem()
                .transformToUni(uuid -> resources.getEndpoint(defaultEndpoint.getTenant(), uuid))
                .merge();
    }
}
