package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Request body for testing an integration endpoint.
 *
 * Based on: common/src/main/java/com/redhat/cloud/notifications/routers/endpoints/EndpointTestRequest.java
 *
 * Note: MCP module does not currently have a compile-time dependency on notifications-common,
 * so this DTO mirrors the structure rather than importing the class directly.
 */
@RegisterForReflection
@JsonNaming(SnakeCaseStrategy.class)
public final class EndpointTestRequestDTO {

    @NotBlank
    public String message;
}
