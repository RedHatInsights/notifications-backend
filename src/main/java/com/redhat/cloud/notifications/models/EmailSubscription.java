package com.redhat.cloud.notifications.models;

public class EmailSubscription {

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
