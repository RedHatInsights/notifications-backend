package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Base class for endpoint properties in MCP requests.
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/EndpointPropertiesDTO.java
 *
 * Mirrors the backend EndpointPropertiesDTO structure for JSON compatibility.
 * Polymorphic deserialization is handled by {@code @JsonTypeInfo} and
 * {@code @JsonSubTypes} on the {@code properties} field in {@link EndpointDTO},
 * using the {@code type} field as an external property discriminator.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public abstract class EndpointPropertiesDTO {
}
