package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;

import java.time.LocalDateTime;
import java.util.Optional;

public abstract class EventDeduplicationConfig {

    protected final Event event;

    public EventDeduplicationConfig(Event event) {
        this.event = event;
    }

    public abstract LocalDateTime getDeleteAfter();

    public abstract Optional<String> getDeduplicationKey();
}
