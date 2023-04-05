package com.redhat.cloud.notifications.models;

public class EventTypeKeyBundleAppEventTriplet implements EventTypeKey {

    private final String bundle;
    private final String application;
    private final String eventType;

    public EventTypeKeyBundleAppEventTriplet(String bundle, String application, String eventType) {
        this.bundle = bundle;
        this.application = application;
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getBundle() {
        return bundle;
    }

    public String getApplication() {
        return application;
    }

    @Override
    public String toString() {
        return String.format("baet=%s/%s/%s", bundle, application, eventType);
    }
}
