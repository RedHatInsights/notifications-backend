package com.redhat.cloud.notifications.routers.internal.userpreferencesmigration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.SubscriptionType;
import java.util.Set;

/**
 * Represents a user that has subscribed to the Patch events.
 * @param username the username of the user.
 * @param orgId the org ID the user belongs to.
 * @param accountId the EBS account number.
 * @param subscriptionPreferences the subscription types the user has enabled.
 */
public record PatchSubscription(
    @JsonProperty("principal") String username,
    @JsonProperty("org_id") String orgId,
    @JsonProperty("ebs_account_number") String accountId,
    @JsonProperty("subscription") Set<SubscriptionType> subscriptionPreferences
) {
}
