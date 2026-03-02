package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;

import java.time.LocalDateTime;
import java.util.Optional;

public class DefaultEventDeduplicationConfig implements EventDeduplicationConfig {

    private static final int DEFAULT_RETENTION_DELAY_IN_DAYS = 1;

    @Override
    public LocalDateTime getDeleteAfter(Event event) {
        return event.getTimestamp().plusDays(DEFAULT_RETENTION_DELAY_IN_DAYS);
    }

    @Override
    public Optional<String> getDeduplicationKey(Event event) {
        if (event.getExternalId() != null) {
            return Optional.of(event.getExternalId().toString());
        }
        // TODO remove check on id with RHCLOUD-44531
        if (event.getId() != null) {
            return Optional.of(event.getId().toString());
        }
        return Optional.empty();
    }
}
