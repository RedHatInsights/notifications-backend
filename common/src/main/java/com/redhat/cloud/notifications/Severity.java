package com.redhat.cloud.notifications;

/**
 * Recognized levels for {@link com.redhat.cloud.notifications.ingress.Action#severity}
 */
public enum Severity {
    CRITICAL,
    IMPORTANT,
    MODERATE,
    LOW,
    NONE,
    UNDEFINED
}
