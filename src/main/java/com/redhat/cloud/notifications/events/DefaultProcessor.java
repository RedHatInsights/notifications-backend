package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.models.Endpoint;
import io.smallrye.mutiny.Multi;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class DefaultProcessor {
    @Inject
    EndpointResources resources;

    public Multi<Endpoint> getDefaultEndpoints(Endpoint defaultEndpoint) {
        return resources.getDefaultEndpoints(defaultEndpoint.getTenant());
    }
}
