package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof EmailAggregationKey)) {
            return false;
        }

        EmailAggregationKey that = (EmailAggregationKey) o;

        return Objects.equals(orgId, that.orgId) &&
                Objects.equals(bundle, that.bundle) &&
                Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, bundle, application);
    }

    @Override
    public String toString() {
        return "EmailAggregationKey{" +
                "orgId='" + orgId + '\'' +
                ", bundle='" + bundle + '\'' +
                ", application='" + application + '\'' +
                '}';
    }
}
