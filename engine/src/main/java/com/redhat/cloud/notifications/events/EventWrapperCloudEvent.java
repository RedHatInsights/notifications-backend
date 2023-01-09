package com.redhat.cloud.notifications.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.cloud.notifications.models.EventTypeKeyFqn;

import java.util.UUID;

public class EventWrapperCloudEvent implements EventWrapper<JsonNode, EventTypeKeyFqn> {

    private final JsonNode cloudEvent;
    private final EventTypeKeyFqn eventTypeKeyFqn;

    public EventWrapperCloudEvent(JsonNode cloudEvent) {
        this.cloudEvent = cloudEvent;
        this.eventTypeKeyFqn = new EventTypeKeyFqn(cloudEvent.get("type").asText());
    }

    @Override
    public EventTypeKeyFqn getKey() {
        return eventTypeKeyFqn;
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
