package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventDeduplicationConfig {

    LocalDateTime getDeleteAfter(Event event);

    Optional<String> getDeduplicationKey(Event event);
}
