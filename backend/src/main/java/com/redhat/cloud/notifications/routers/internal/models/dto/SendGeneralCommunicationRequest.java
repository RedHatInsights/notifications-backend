package com.redhat.cloud.notifications.routers.internal.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Defines the expected payload for the "send general communication" request.
 * @param sendGeneralCommunication the safety key that must be present in the
 *                                 payload.
 */
public record SendGeneralCommunicationRequest(
    @JsonProperty("send_general_communication") @NotNull Boolean sendGeneralCommunication
) {
}
