package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Supported Camel connector sub-types.
 * Only applicable when endpoint type is CAMEL.
 *
 * This enum provides compile-time safety and generates a JSON Schema enum constraint
 * for AI agents, preventing invalid subType values from reaching the backend.
 *
 * Note: Backend stores subType as a String. Jackson serializes this enum to lowercase
 * strings using the @JsonProperty annotations (e.g., SLACK -> "slack").
 */
@RegisterForReflection
public enum CamelSubType {
    @JsonProperty("slack")
    SLACK,

    @JsonProperty("teams")
    TEAMS,

    @JsonProperty("google_chat")
    GOOGLE_CHAT,

    @JsonProperty("servicenow")
    SERVICENOW,

    @JsonProperty("splunk")
    SPLUNK
}
