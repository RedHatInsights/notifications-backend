package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.EventTypeKey;

import java.util.UUID;

/**
 * Event Data holds the data received from the event.
 * Provides methods to extract the common data needed to dispatch the event
 */
public interface EventData<T, K extends EventTypeKey> {
    K getEventTypeKey();

    T getRawEvent();

    UUID getId();

    String getOrgId();

    String getAccountId();
}
