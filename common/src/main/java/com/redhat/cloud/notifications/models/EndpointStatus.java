package com.redhat.cloud.notifications.models;

public enum EndpointStatus {
    READY, // 0, default for existing ones
    UNKNOWN,
    NEW,
    PROVISIONING,
    DELETING,
    FAILED
}
