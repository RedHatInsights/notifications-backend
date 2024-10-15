package com.redhat.cloud.notifications.models.dto.v1.endpoint;

public enum EndpointStatusDTO {
    DELETING,
    FAILED,
    NEW,
    PROVISIONING,
    READY, // 0, default for existing ones
    UNKNOWN
}
