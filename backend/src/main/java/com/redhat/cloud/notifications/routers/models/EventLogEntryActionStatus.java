package com.redhat.cloud.notifications.routers.models;

// User facing Notification Status
public enum EventLogEntryActionStatus {
    SENT,
    SUCCESS,
    PROCESSING,
    // Encompass the FAILED_INTERNAL and FAILED_EXTERNAL
    FAILED,
    UNKNOWN;
}
