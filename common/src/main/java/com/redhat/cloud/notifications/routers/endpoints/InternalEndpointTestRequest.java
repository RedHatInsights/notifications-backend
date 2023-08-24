package com.redhat.cloud.notifications.routers.endpoints;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Represents the internal request that will be sent from the backend to the
 * engine in order to trigger an "endpoint test".
 */
@JsonNaming(SnakeCaseStrategy.class)
public class InternalEndpointTestRequest {

    @NotNull
    public UUID endpointUuid;

    public String message;

    @NotBlank
    public String orgId;

    /**
     * Checks if the message is null or blank.
     * @return true if the message is null or blank.
     */
    public boolean isMessageBlank() {
        return this.message == null
            || this.message.isBlank();
    }
}
