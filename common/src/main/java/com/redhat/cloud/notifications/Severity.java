package com.redhat.cloud.notifications;

/**
 * Recognized levels for {@link com.redhat.cloud.notifications.ingress.Action#severity Action#severity}. Values must be kept in
 * order of decreasing severity to be properly sorted.
 *
 * @see <a href="https://access.redhat.com/security/updates/classification">Red Hat severity ratings</a>
 * @see <a href="https://www.patternfly.org/patterns/status-and-severity/#severity-icons">PatternFly severity icons</a>
 */
public enum Severity {
    CRITICAL,
    IMPORTANT,
    MODERATE,
    LOW,
    NONE,

    /** A severity level was not provided, or could not be parsed. */
    UNDEFINED
}
