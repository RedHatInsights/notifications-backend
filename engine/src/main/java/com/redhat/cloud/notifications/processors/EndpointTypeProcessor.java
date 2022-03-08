package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;

import java.util.List;

public interface EndpointTypeProcessor {
    List<NotificationHistory> process(Event event, List<Endpoint> endpoints);
}
