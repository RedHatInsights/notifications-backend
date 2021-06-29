package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;

import java.util.List;

public interface EndpointTypeProcessor {
    Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints);
}
