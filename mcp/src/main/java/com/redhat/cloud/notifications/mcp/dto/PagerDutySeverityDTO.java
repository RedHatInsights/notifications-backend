package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Locale;

/**
 * PagerDuty severity levels.
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/PagerDutySeverityDTO.java
 */
@RegisterForReflection
public enum PagerDutySeverityDTO {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("error")
    ERROR,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("info")
    INFO;

    /**
     * Parses the lowercase or uppercase severity into this enum.
     *
     * @return a {@link PagerDutySeverityDTO} constant
     */
    public static PagerDutySeverityDTO fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ENGLISH));
    }
}
