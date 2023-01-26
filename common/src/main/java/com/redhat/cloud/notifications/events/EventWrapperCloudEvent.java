package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.ConsoleCloudEvent;
import com.redhat.cloud.notifications.models.EventTypeKeyFqn;

import java.util.UUID;

public class EventWrapperCloudEvent implements EventWrapper<ConsoleCloudEvent, EventTypeKeyFqn> {

    private final ConsoleCloudEvent cloudEvent;
    private final EventTypeKeyFqn eventTypeKeyFqn;

    public EventWrapperCloudEvent(ConsoleCloudEvent cloudEvent) {
        this.cloudEvent = cloudEvent;
        this.eventTypeKeyFqn = new EventTypeKeyFqn(cloudEvent.getType());
    }

    @Override
    public EventTypeKeyFqn getKey() {
        return eventTypeKeyFqn;
    }

    @Override
    public ConsoleCloudEvent getEvent() {
        return cloudEvent;
    }

    @Override
    public UUID getId() {
        return cloudEvent.getId();
    }

    @Override
    public String getOrgId() {
        return cloudEvent.getOrgId();
    }

    @Override
    public String getAccountId() {
        return cloudEvent.getAccountId();
    }
}
