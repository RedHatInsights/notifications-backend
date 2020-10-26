package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventBusTypeProcessor implements EndpointTypeProcessor {
    @Inject
    Vertx vertx;

    public Uni<NotificationHistory> process(Notification notification) {
        return Uni.createFrom().nullItem();
//        return vertx.eventBus().publisher(getAddress(notification.getTenant())).write(notification.toString());
    }

    public static String getAddress(String tenantId) {
        return String.format("notifications-%s", tenantId);
    }
}
