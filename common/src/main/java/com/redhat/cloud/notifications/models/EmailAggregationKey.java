package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class EmailAggregationKey {

    @NotNull
    private String orgId;

    @NotNull
    private String bundle;

    public EmailAggregationKey(String orgId, String bundle) {
        this.orgId = orgId;
        this.bundle = bundle;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getBundle() {
        return bundle;
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
                Objects.equals(bundle, that.bundle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, bundle);
    }

    @Override
    public String toString() {
        return "EmailAggregationKey{" +
                "orgId='" + orgId + '\'' +
                ", bundle='" + bundle + '\'' +
                '}';
    }
}
