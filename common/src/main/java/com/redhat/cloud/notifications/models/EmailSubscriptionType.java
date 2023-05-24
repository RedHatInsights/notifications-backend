package com.redhat.cloud.notifications.models;

import java.time.Duration;

public enum EmailSubscriptionType {
    INSTANT(null, true),
    DAILY(Duration.ofDays(1), true),
    DRAWER(null, false);


    private Duration duration;
    private Boolean optIn;

    EmailSubscriptionType(Duration duration, boolean optIn) {
        this.duration = duration;
        this.optIn = optIn;
    }

    public Duration getDuration() {
        return this.duration;
    }

    public Boolean isOptIn() {
        return optIn;
    }

    // This may seem unused but it is actually required for a RestEasy request parameter deserialization.
    public static EmailSubscriptionType fromString(String value) {
        return EmailSubscriptionType.valueOf(value.toUpperCase());
    }

    public Boolean isOptOut() {
        return !optIn;
    }
}
