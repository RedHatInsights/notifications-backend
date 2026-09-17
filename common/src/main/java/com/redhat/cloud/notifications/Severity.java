package com.redhat.cloud.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Recognized levels for {@link com.redhat.cloud.notifications.ingress.Action#severity Action#severity}. Values must be kept in
 * order of decreasing severity to be properly sorted.
 *
 * @see <a href="https://access.redhat.com/security/updates/classification">Red Hat severity ratings</a>
 * @see <a href="https://www.patternfly.org/patterns/status-and-severity/#severity-icons">PatternFly severity icons</a>
 */
@Schema(enumeration = { "CRITICAL", "IMPORTANT", "MODERATE", "LOW", "NONE", "UNDEFINED"})
public enum Severity {
    @JsonProperty("CRITICAL")
    CRITICAL(10),
    @JsonProperty("IMPORTANT")
    IMPORTANT(20),
    @JsonProperty("MODERATE")
    MODERATE(30),
    @JsonProperty("LOW")
    LOW(40),
    @JsonProperty("NONE")
    NONE(50),
    /** A severity level was not provided, or could not be parsed. Do not display this value in outgoing notifications. */
    @JsonProperty("UNDEFINED")
    UNDEFINED(60);

    // Assigned to rows with NULL or unrecognized severity (e.g. legacy pre-severity events).
    public static final short UNKNOWN_SEVERITY_ORDER = 70;

    private final short order;

    Severity(int order) {
        this.order = (short) order;
    }

    public short getOrder() {
        return order;
    }
}
