package com.redhat.cloud.notifications.models;

import java.time.Duration;

public enum EmailSubscriptionType {
    INSTANT("INSTANT", null),
    DAILY("DAILY", Duration.ofDays(1));

    private String name;
    private Duration duration;

    EmailSubscriptionType(String name, Duration duration) {
        this.name = name;
        this.duration = duration;
    }

    public Duration getDuration() {
        return this.duration;
    }

    public String toString() {
        return this.name;
    }

    public static EmailSubscriptionType fromString(String value) {
        for (EmailSubscriptionType type : EmailSubscriptionType.values()) {
            if (type.toString().equals(value.toUpperCase())) {
                return type;
            }
        }

        throw new RuntimeException("Unknow EmailSubscriptionType " + value);
    }
}
