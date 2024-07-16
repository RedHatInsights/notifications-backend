package com.redhat.cloud.notifications.connector.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response sent by the engine when requested for a payload.
 * @param contents the received payload contents.
 */
public record PayloadDetails(@JsonProperty("contents") String contents) {
    /**
     * The key for the identifier of the payload which will go in the JSON
     * payload that we send over Kafka.
     */
    public static final String PAYLOAD_DETAILS_ID_KEY = "payload_details_id";
}
