package com.redhat.cloud.notifications.routers.endpoints;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotBlank;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Represents the structure of the "test endpoint" request that the user might
 * send.
 */
@JsonNaming(SnakeCaseStrategy.class)
public final class EndpointTestRequest {

    @NotBlank
    public String message;
}
