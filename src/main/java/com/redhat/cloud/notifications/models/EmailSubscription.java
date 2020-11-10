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
    private EventType eventType;

}
