package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;

public interface EndpointTypeProcessor {
    Uni<NotificationHistory> process(Notification notification);
}
