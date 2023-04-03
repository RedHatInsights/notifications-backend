package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.EventTypeKeyFqn;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;

import java.util.UUID;

public class EventWrapperCloudEvent implements EventWrapper<NotificationsConsoleCloudEvent, EventTypeKeyFqn> {

    private final NotificationsConsoleCloudEvent cloudEvent;
    private final EventTypeKeyFqn eventTypeKeyFqn;

    public EventWrapperCloudEvent(NotificationsConsoleCloudEvent cloudEvent) {
        this.cloudEvent = cloudEvent;
        this.eventTypeKeyFqn = new EventTypeKeyFqn(cloudEvent.getType());
    }

    @Override
    public EventTypeKeyFqn getKey() {
        return eventTypeKeyFqn;
    }

    @Override
    public NotificationsConsoleCloudEvent getEvent() {
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
