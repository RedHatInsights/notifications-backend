package com.redhat.cloud.notifications.processors.payload.dto.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ReadPayloadDetailsDto {
    private final String contents;

    public ReadPayloadDetailsDto(@JsonProperty("contents") final String contents) {
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }
}
