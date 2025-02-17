package com.redhat.cloud.notifications.routers.general.communication;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response's structure that will come from the engine.
 * @param info Any information that the engine might have included in the
 *             response body.
 */
public record SendGeneralCommunicationResponse(
    @JsonProperty("info") String info
) {
}
