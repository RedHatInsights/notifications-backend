package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EventTypeKeyBundleAppEventTriplet;

import java.util.UUID;

public class EventWrapperAction implements EventWrapper<Action, EventTypeKeyBundleAppEventTriplet> {

    private final Action action;
    private final EventTypeKeyBundleAppEventTriplet eventTypeKeyBundleAppEventTriplet;

    public EventWrapperAction(Action action) {
        this.action = action;
        this.eventTypeKeyBundleAppEventTriplet = new EventTypeKeyBundleAppEventTriplet(
                action.getBundle(),
                action.getApplication(),
                action.getEventType()
        );
    }

    @Override
    public EventTypeKeyBundleAppEventTriplet getKey() {
        return eventTypeKeyBundleAppEventTriplet;
    }

    @Override
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
