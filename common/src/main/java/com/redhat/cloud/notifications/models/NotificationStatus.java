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
    // The notification was sent to the processor - but we have no way to tell if it was processed succesfully or if it has failed (yet)
    SENT,
    // the notification processed successfully - either locally or by a different processor
    SUCCESS;

    public Boolean toInvocationResult() {
        return this == SUCCESS || this == SENT;
    }
}
