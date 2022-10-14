package com.redhat.cloud.notifications.models;

public enum NotificationStatus {
    // For any error that happens in Notifications, including when we fail to send an event to a different processor (i.e. RHOSE)
    FAILED_INTERNAL,
    // The notification failed to be processed and the processor returned us an error
    FAILED_EXTERNAL,
    // The notification is being processed
    // A notification was sent to a different processor and we are waiting for it's output
    PROCESSING,
    // The notification was sent to the processor - but we have no way to tell if it was processed successfully or if it has failed (yet)
    SENT,
    // the notification processed successfully - either locally or by a different processor
    SUCCESS;

    public Boolean toInvocationResult() {
        return this == SUCCESS || this == SENT;
    }
}
