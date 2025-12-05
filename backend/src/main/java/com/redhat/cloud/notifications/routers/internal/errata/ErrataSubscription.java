package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.models.EventType;

import java.util.Set;

/**
 * Represents a user that has subscribed to the TeamNado events.
 * @param username the username of the user.
 * @param org_id the org ID the user belongs to.
 * @param eventTypeSubscriptions the event types the user is subscribed to.
 */
public record ErrataSubscription(String username, String org_id, Set<EventType> eventTypeSubscriptions) {
}
