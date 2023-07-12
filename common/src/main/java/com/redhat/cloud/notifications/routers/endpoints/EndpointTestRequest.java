package com.redhat.cloud.notifications.routers.endpoints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotBlank;

/**
 * Represents the structure of the "test endpoint" request that the user might
 * send.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EndpointTestRequest {
    @NotBlank
    public String message;

    public EndpointTestRequest(@JsonProperty("message") final String message) {
        this.message = message;
    }
}
