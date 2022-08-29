package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;

public class EmailAggregationKey {

    private String accountId;

    @NotNull
    private String orgId;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    public EmailAggregationKey(String accountId, String orgId, String bundle, String application) {
        this.accountId = accountId;
        this.orgId = orgId;
        this.bundle = bundle;
        this.application = application;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getBundle() {
        return bundle;
    }

    public String getApplication() {
        return application;
    }

}
