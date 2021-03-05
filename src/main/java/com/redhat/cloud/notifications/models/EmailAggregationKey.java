package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;

public class EmailAggregationKey {

    @NotNull
    private String accountId;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    public EmailAggregationKey() {
    }

    public EmailAggregationKey(String accountId, String bundle, String application) {
        this.accountId = accountId;
        this.bundle = bundle;
        this.application = application;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

}
