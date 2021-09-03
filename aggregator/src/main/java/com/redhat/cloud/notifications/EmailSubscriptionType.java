package com.redhat.cloud.notifications;

import java.time.Duration;

public enum EmailSubscriptionType {

    DAILY(Duration.ofDays(1));

    private final Duration duration;

    EmailSubscriptionType(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return this.duration;
    }
}
