package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EventTypeTripletKey;

import java.util.UUID;

public class EventWrapperAction implements EventWrapper<Action, EventTypeTripletKey> {

    private final Action action;
    private final EventTypeTripletKey eventTypeTripletKey;

    public EventWrapperAction(Action action) {
        this.action = action;
        this.eventTypeTripletKey = new EventTypeTripletKey(
                action.getBundle(),
                action.getApplication(),
                action.getEventType()
        );
    }

    @Override
    public EventTypeTripletKey getKey() {
        return eventTypeTripletKey;
    }

    public Action getEvent() {
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
