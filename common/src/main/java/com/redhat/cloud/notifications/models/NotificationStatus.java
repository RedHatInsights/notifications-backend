package com.redhat.cloud.notifications.models;

public enum NotificationStatus {
    // The notification failed to be created for processing
    // This could happen when sending the notification to a different processor and it was rejected before entering a processing phase
    FAILED_CREATION,
    // The notification is being processed
    // A notification was sent to a different processor and we are waiting for it's output
    PROCESSING,
    // The notification has been in processing state for too long
    PROCESSING_TIMEOUT,
    // the notification failed to be processed
    FAILED_PROCESSING,
    // the notification processed successfully - either locally or by a different processor
    SUCCESS;

    public Boolean toInvocationResult() {
        return this == SUCCESS;
    }
}
