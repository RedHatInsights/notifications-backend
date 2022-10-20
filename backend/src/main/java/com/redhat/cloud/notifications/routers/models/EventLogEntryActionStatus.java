package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.models.NotificationStatus;
import io.quarkus.logging.Log;

// User facing Notification Status
public enum EventLogEntryActionStatus {
    SENT,
    SUCCESS,
    PROCESSING,
    // Encompass the FAILED_INTERNAL and FAILED_EXTERNAL
    FAILED,
    UNKNOWN;

    public static EventLogEntryActionStatus fromNotificationStatus(NotificationStatus status) {
        switch (status) {
            case SENT:
                return EventLogEntryActionStatus.SENT;
            case SUCCESS:
                return EventLogEntryActionStatus.SUCCESS;
            case PROCESSING:
                return EventLogEntryActionStatus.PROCESSING;
            case FAILED_EXTERNAL:
            case FAILED_INTERNAL:
                return EventLogEntryActionStatus.FAILED;
            default:
                Log.warnf("Uncovered status found:[%s]. This is a bug", status);
                return EventLogEntryActionStatus.UNKNOWN;
        }
    }
}
