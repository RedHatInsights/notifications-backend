package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class EmailAggregationKey {

    @NotNull
    private String accountId;

    private String orgId;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    public EmailAggregationKey(String accountId, String bundle, String application) {
        this.accountId = accountId;
        this.bundle = bundle;
        this.application = application;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof EmailAggregationKey)) {
            return false;
        }

        EmailAggregationKey that = (EmailAggregationKey) o;

        // TODO NOTIF-603 Add orgId
        return Objects.equals(accountId, that.accountId) && Objects.equals(bundle, that.bundle) && Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        // TODO NOTIF-603 Add orgId
        return Objects.hash(accountId, bundle, application);
    }

    @Override
    public String toString() {
        return "EmailAggregationKey{" +
                "accountId='" + accountId + '\'' +
                ", bundle='" + bundle + '\'' +
                ", application='" + application + '\'' +
                '}';
    }
}
