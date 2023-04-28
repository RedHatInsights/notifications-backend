package com.redhat.cloud.notifications.routers.endpoints;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Represents the internal request that will be sent from the backend to the
 * engine in order to trigger an "endpoint test".
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalEndpointTestRequest {

    @NotNull
    public UUID endpointUuid;

    public String message;

    @NotBlank
    public String orgId;

    public InternalEndpointTestRequest(final UUID endpointUuid, final String orgId) {
        this.endpointUuid = endpointUuid;
        this.orgId = orgId;
    }

    public InternalEndpointTestRequest(
        @JsonProperty("endpointUuid") final UUID endpointUuid,
        @JsonProperty("message") final String message,
        @JsonProperty("orgId") final String orgId
    ) {
        this.endpointUuid = endpointUuid;
        this.message = message;
        this.orgId = orgId;
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
