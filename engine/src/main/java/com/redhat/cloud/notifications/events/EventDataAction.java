package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EventTypeKey;
import com.redhat.cloud.notifications.models.EventTypeTripletKey;

import java.util.UUID;

public class EventDataAction implements EventData<Action> {

    private final Action action;
    private final EventTypeTripletKey eventTypeTripletKey;

    public EventDataAction(Action action) {
        this.action = action;
        this.eventTypeTripletKey = new EventTypeTripletKey(
                action.getBundle(),
                action.getApplication(),
                action.getEventType()
        );
    }

    @Override
    public EventTypeKey getEventTypeKey() {
        return eventTypeTripletKey;
    }

    public Action getRawEvent() {
        return action;
    }

    @Override
    public UUID getId() {
        return action.getId();
    }

    @Override
    public String getOrgId() {
        return action.getOrgId();
    }

    @Override
    public String getAccountId() {
        return action.getAccountId();
    }
}
