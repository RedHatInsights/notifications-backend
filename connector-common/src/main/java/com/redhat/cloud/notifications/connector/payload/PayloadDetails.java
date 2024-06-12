package com.redhat.cloud.notifications.connector.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class PayloadDetails {
    private final String contents;

    public PayloadDetails(@JsonProperty("contents") final String contents) {
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }
}
