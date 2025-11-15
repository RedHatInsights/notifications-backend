package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.ingress.Action;

import java.time.LocalDateTime;

/**
 * Minimal action builder.
 * Add more fields if needed.
 */
public class ActionBuilder {

    public static Action build(LocalDateTime timestamp) {
        return new Action.ActionBuilder()
            .withTimestamp(timestamp)
            .build();
    }
}
