package com.redhat.cloud.notifications.models;

import java.time.Duration;

public class EmailSubscription {

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

    private String accountId;
    private String username;
    private String bundle;
    private String application;
    private EmailSubscriptionType type;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public EmailSubscriptionType getType() {
        return type;
    }

    public void setType(EmailSubscriptionType type) {
        this.type = type;
    }

}
