package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

public class EventAggregationCriteria extends EmailAggregationKey {

    @NotNull
    private final UUID bundleId;

    @NotNull
    private final UUID applicationId;

    public EventAggregationCriteria(String orgId, UUID bundleId, UUID applicationId, String bundle, String application) {
        super(orgId, bundle, application);
        this.bundleId = bundleId;
        this.applicationId = applicationId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        EmailAggregationKey that = (EmailAggregationKey) o;
        return Objects.equals(getOrgId(), that.getOrgId()) &&
            Objects.equals(getBundle(), that.getBundle()) &&
            Objects.equals(getApplication(), that.getApplication());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOrgId(), getBundle(), getApplication());
    }

    @Override
    public String toString() {
        return "EventAggregationCriteria{" +
            "orgId='" + getOrgId() + '\'' +
            ", bundleId=" + bundleId +
            ", bundleName=" + getBundle() +
            ", applicationId=" + applicationId +
            ", applicationName=" + getApplication() +
            '}';
    }
}
