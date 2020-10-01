package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventBusTypeProcessor implements EndpointTypeProcessor {
    @Inject
    Vertx vertx;

    public Uni<Void> process(Notification notification) {
        return vertx.eventBus().publisher(getAddress(notification.getTenant())).write(notification);
    }

    public static String getAddress(String tenantId) {
        return String.format("notifications-%s", tenantId);
    }
}
