package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;

import java.time.LocalDateTime;

public class DefaultEventDeduplicationConfig extends EventDeduplicationConfig {

    private static final int DEFAULT_RETENTION_DELAY_IN_DAYS = 1;

    public DefaultEventDeduplicationConfig(Event event) {
        super(event);
    }

    @Override
    public LocalDateTime getDeleteAfter() {
        return getEventTimestamp().plusDays(DEFAULT_RETENTION_DELAY_IN_DAYS);
    }

    @Override
    public String getDeduplicationKey() {
        return event.getId().toString();
    }
}
