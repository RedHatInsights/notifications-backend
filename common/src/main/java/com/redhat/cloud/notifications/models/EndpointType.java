package com.redhat.cloud.notifications.models;

public enum EndpointType {
    WEBHOOK(false, false),
    EMAIL_SUBSCRIPTION(false, true),
    CAMEL(true, false),
    ANSIBLE(false, false),
    DRAWER(false, true);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointType(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }

}
