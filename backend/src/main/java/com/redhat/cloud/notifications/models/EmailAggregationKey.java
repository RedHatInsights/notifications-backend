package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof EmailAggregationKey)) {
            return false;
        }

        EmailAggregationKey that = (EmailAggregationKey) o;
        return Objects.equals(accountId, that.accountId) && Objects.equals(bundle, that.bundle) && Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, bundle, application);
    }
}
