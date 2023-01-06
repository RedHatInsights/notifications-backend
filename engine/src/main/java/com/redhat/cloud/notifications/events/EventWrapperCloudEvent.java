package com.redhat.cloud.notifications.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.cloud.notifications.models.EventTypeFqnKey;

import java.util.UUID;

public class EventWrapperCloudEvent implements EventWrapper<JsonNode, EventTypeFqnKey> {

    private final JsonNode cloudEvent;
    private final EventTypeFqnKey eventTypeFqnKey;

    public EventWrapperCloudEvent(JsonNode cloudEvent) {
        this.cloudEvent = cloudEvent;
        this.eventTypeFqnKey = new EventTypeFqnKey(cloudEvent.get("type").asText());
    }

    @Override
    public EventTypeFqnKey getKey() {
        return eventTypeFqnKey;
    }

    @Override
    public JsonNode getEvent() {
        return cloudEvent;
    }

    @Override
    public UUID getId() {
        return UUID.fromString(cloudEvent.get("id").asText());
    }

    @Override
    public String getOrgId() {
        return cloudEvent.get("redhatorgid").asText();
    }

    @Override
    public String getAccountId() {
        return cloudEvent.get("redhataccount").asText();
    }
}
