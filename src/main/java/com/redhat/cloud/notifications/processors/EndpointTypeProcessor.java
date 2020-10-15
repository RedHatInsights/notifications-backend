package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.models.Notification;
import io.smallrye.mutiny.Uni;

public interface EndpointTypeProcessor {
    Uni<Void> process(Notification notification);
}
