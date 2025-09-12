package com.redhat.cloud.notifications.connector.email.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response sent by the engine when requested for a payload.
 * @param contents the received payload contents.
 */
public record PayloadDetails(@JsonProperty("contents") String contents) {
}
