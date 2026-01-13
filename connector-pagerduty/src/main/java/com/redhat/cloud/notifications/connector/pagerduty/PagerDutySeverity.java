package com.redhat.cloud.notifications.connector.pagerduty;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/** @apiNote For converting from a string, use {@link #fromJson(String)} instead of {@link #valueOf(String)}. */
public enum PagerDutySeverity {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("error")
    ERROR,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("info")
    INFO;

    /**
     * Default value of {@link PagerDutySeverity#WARNING} indicates that action may be required, without triggering high-urgency notification rules,
     * based on <a href="https://support.pagerduty.com/main/docs/dynamic-notifications#severity-and-urgency-mapping">PagerDuty documentation</a>
     */
    public static PagerDutySeverity defaultValue() {
        return PagerDutySeverity.WARNING;
    }

    /** Parses the lowercase or uppercase severity into this enum
     *
     * @return a {@link PagerDutySeverity} constant
     */
    public static PagerDutySeverity fromJson(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ignored) {
            return defaultValue();
        }
    }

    /** Maps from security's {@code Severity} to PagerDuty-native levels. */
    public static PagerDutySeverity fromSecuritySeverity(String severity) {
        return switch (severity) {
            case "CRITICAL" -> CRITICAL;
            case "IMPORTANT" -> ERROR;
            case "MODERATE" -> WARNING;
            case "LOW", "NONE", "UNDEFINED" -> INFO;
            default -> defaultValue();
        };
    }
}
