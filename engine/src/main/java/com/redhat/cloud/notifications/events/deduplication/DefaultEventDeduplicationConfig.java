package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;

import java.time.LocalDateTime;
import java.util.Optional;

public class DefaultEventDeduplicationConfig extends EventDeduplicationConfig {

    private static final int DEFAULT_RETENTION_DELAY_IN_DAYS = 1;

    public DefaultEventDeduplicationConfig(Event event) {
        super(event);
    }

    @Override
    public LocalDateTime getDeleteAfter() {
        return event.getTimestamp().plusDays(DEFAULT_RETENTION_DELAY_IN_DAYS);
    }

    @Override
    public Optional<String> getDeduplicationKey() {
        if (event.getId() != null) {
            return Optional.of(event.getId().toString());
        }
        return Optional.empty();
    }
}
