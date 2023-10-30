package com.redhat.cloud.notifications.models;

import java.time.Duration;

public enum SubscriptionType {
    INSTANT(null, false),
    DAILY(Duration.ofDays(1), false),
    DRAWER(null, true);

    private final Duration duration;
    private final boolean subscribedByDefault;

    SubscriptionType(Duration duration, boolean subscribedByDefault) {
        this.duration = duration;
        this.subscribedByDefault = subscribedByDefault;
    }

    public Duration getDuration() {
        return duration;
    }

    public boolean isSubscribedByDefault() {
        return subscribedByDefault;
    }

    // This may seem unused but it is actually required for a RestEasy request parameter deserialization.
    public static SubscriptionType fromString(String value) {
        return SubscriptionType.valueOf(value.toUpperCase());
    }
}
