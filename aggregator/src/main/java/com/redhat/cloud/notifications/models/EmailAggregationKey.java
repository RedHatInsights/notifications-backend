package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;

public class EmailAggregationKey {

    @NotNull
    private String orgId;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    public EmailAggregationKey(String orgId, String bundle, String application) {
        this.orgId = orgId;
        this.bundle = bundle;
        this.application = application;
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
