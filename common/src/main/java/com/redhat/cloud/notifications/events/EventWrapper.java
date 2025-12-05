package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.EventTypeKey;

import java.util.UUID;

/**
 * Wraps the event received to abstract some common methods used for routing the event.
 */
public interface EventWrapper<T, K extends EventTypeKey> {
    K getKey();

    T getEvent();

    UUID getId();

    String getOrgId();

    String getAccountId();
}
