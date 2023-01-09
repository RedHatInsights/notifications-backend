package com.redhat.cloud.notifications.models;

public class EventTypeKeyFqn implements EventTypeKey {

    private final String fullyQualifiedName;

    public EventTypeKeyFqn(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public String toString() {
        return String.format("eventFqn=%s", fullyQualifiedName);
    }
}
