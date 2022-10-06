package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class CamelHistoryFillerHelper {

    @Inject
    NotificationHistoryRepository notificationHistoryRepository;

    /**
     * Update a stub history item with data we have received from the Camel sender
     *
     * @param jo Map containing the returned data
     * @return Nothing
     * @see com.redhat.cloud.notifications.events.FromCamelHistoryFiller for the source of data
     */
    public void updateHistoryItem(Map<String, Object> jo) {
        String historyId = (String) jo.get("historyId");

        if (historyId == null || historyId.isBlank()) {
            throw new IllegalArgumentException("History Id is null");
        }

        String outcome = (String) jo.get("outcome");
        // TODO NOTIF-636 Remove oldResult after the Eventing team is done integrating with the new way to determine the success.
        boolean oldResult = outcome != null && outcome.startsWith("Success");
        boolean result = oldResult || jo.containsKey("successful") && ((Boolean) jo.get("successful"));
        Map<String, Object> details = (Map) jo.get("details");
        if (!details.containsKey("outcome")) {
            details.put("outcome", outcome);
        }

        NotificationStatus status = result ? NotificationStatus.SUCCESS : NotificationStatus.FAILED_PROCESSING;

        Integer duration = (Integer) jo.getOrDefault("duration", 0);

        NotificationHistory history = new NotificationHistory();

        history.setId(UUID.fromString(historyId));
        history.setStatus(status);
        history.setDetails(details);
        history.setInvocationTime(duration.longValue());

        notificationHistoryRepository.updateHistoryItem(history);
    }
}
