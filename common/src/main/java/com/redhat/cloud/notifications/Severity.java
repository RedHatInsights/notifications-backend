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
    CRITICAL,
    @JsonProperty("IMPORTANT")
    IMPORTANT,
    @JsonProperty("MODERATE")
    MODERATE,
    @JsonProperty("LOW")
    LOW,
    @JsonProperty("NONE")
    NONE,
    /** A severity level was not provided, or could not be parsed. */
    @JsonProperty("UNDEFINED")
    UNDEFINED
}
