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
    public boolean updateHistoryItem(Map<String, Object> jo) {
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

        NotificationStatus status = result ? NotificationStatus.SUCCESS : NotificationStatus.FAILED_EXTERNAL;

        // The duration may be an Integer or a Long depending on its value.
        long duration = ((Number) jo.getOrDefault("duration", 0)).longValue();
        /*
         * The duration is currently stored as an integer in Postgres. Any value bigger than Integer.MAX_VALUE will
         * cause a PSQLException because the value will be out of the integer range. We need to change the DB column
         * type from integer to bigint but this is an expensive and tricky update because it will lock the table and
         * shut down some parts of the app for a while. Until we've figured out how to best deal with the SQL schema
         * migration, the following line will be used as a workaround to make sure the history is persisted even if
         * the actual duration is bigger than an integer.
         * TODO Remove the following line ASAP.
         */
        duration = Math.min(duration, Integer.MAX_VALUE);

        NotificationHistory history = new NotificationHistory();

        history.setId(UUID.fromString(historyId));
        history.setStatus(status);
        history.setDetails(details);
        history.setInvocationTime(duration);

        return notificationHistoryRepository.updateHistoryItem(history);
    }
}
