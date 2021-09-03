package com.redhat.cloud.notifications.models.aggregation;

import javax.validation.constraints.NotNull;

public class EmailAggregationKey {

    @NotNull
    private String accountId;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    public EmailAggregationKey(String accountId, String bundle, String application) {
        this.accountId = accountId;
        this.bundle = bundle;
        this.application = application;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getBundle() {
        return bundle;
    }

    public String getApplication() {
        return application;
    }

}
