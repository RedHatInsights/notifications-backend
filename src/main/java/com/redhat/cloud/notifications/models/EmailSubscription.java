package com.redhat.cloud.notifications.models;

public class EmailSubscription {

    public enum EmailSubscriptionType {
        INSTANT("INSTANT"),
        DAILY("DAILY");

        private String name;

        EmailSubscriptionType(String name) {
            this.name = name;
        }

        public String toString() {
            return this.name;
        }
    }

    private String accountId;
    private String username;
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

    public EmailSubscriptionType getType() {
        return type;
    }

    public void setType(EmailSubscriptionType type) {
        this.type = type;
    }

}
