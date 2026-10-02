package com.redhat.cloud.notifications.models;

import java.time.Duration;

public enum SubscriptionType {
    INSTANT(null, false, true),
    DAILY(Duration.ofDays(1), false, true),
    WEEKLY(Duration.ofDays(7), false, false),
    DRAWER(null, true, true);

    private final Duration duration;
    private final boolean subscribedByDefault;
    private final boolean userSubscription;

    SubscriptionType(Duration duration, boolean subscribedByDefault, boolean userSubscription) {
        this.duration = duration;
        this.subscribedByDefault = subscribedByDefault;
        this.userSubscription = userSubscription;
    }

    public Duration getDuration() {
        return duration;
    }

    public boolean isSubscribedByDefault() {
        return subscribedByDefault;
    }

    public boolean isUserSubscription() {
        return userSubscription;
    }

    // This may seem unused but it is actually required for a RestEasy request parameter deserialization.
    public static SubscriptionType fromString(String value) {
        return SubscriptionType.valueOf(value.toUpperCase());
    }
}
