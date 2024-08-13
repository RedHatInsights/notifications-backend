package com.redhat.cloud.notifications.routers.internal.errata;

/**
 * Represents a user that has subscribed to the TeamNado events.
 * @param username the username of the user.
 * @param org_id the org ID the user belongs to.
 */
public record ErrataSubscription(String username, String org_id) {
}
