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

    /** Parses the lowercase or uppercase severity into this enum
     *
     * @return a {@link PagerDutySeverity} constant
     */
    public static PagerDutySeverity fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ENGLISH));
    }
}
