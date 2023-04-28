package com.redhat.cloud.notifications.routers.endpoints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Represents the structure of the "test endpoint" request that the user might
 * send.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EndpointTestRequest {
    public String message;

    public EndpointTestRequest(@JsonProperty("message") final String message) {
        this.message = message;
    }

    /**
     * Checks if the message is null or blank.
     * @return true if the message is null or blank.
     */
    public boolean isMessageBlank() {
        return this.message == null
            || this.message.isBlank();
    }
}
