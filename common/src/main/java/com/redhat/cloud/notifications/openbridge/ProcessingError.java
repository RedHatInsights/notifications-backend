package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 *
 */
public class ProcessingError {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ")
    @JsonProperty("recorded_at")
    private ZonedDateTime recordedAt;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("payload")
    private JsonNode payload;

    public ZonedDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(ZonedDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
