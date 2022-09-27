package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.logging.Log;

import javax.inject.Inject;
import java.util.List;

public abstract class EndpointTypeProcessor {

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    public abstract void process(Event event, List<Endpoint> endpoints);

    protected void persistNotificationHistory(NotificationHistory history) {
        try {
            notificationHistoryRepository.createNotificationHistory(history);
        } catch (Exception e) {
            Log.errorf("Notification history creation failed for %s", history.getEndpoint());
        }
    }
}
