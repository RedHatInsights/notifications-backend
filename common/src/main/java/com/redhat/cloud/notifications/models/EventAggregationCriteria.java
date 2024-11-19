package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

public class EventAggregationCriteria extends EmailAggregationKey {

    @NotNull
    private UUID bundleId;

    @NotNull
    private UUID applicationId;

    public EventAggregationCriteria(String orgId, UUID bundleId, UUID applicationId, String bundleName, String applicationName) {
        super(orgId, bundleName, applicationName);
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventAggregationCriteria that = (EventAggregationCriteria) o;
        return Objects.equals(getOrgId(), that.getOrgId()) && Objects.equals(bundleId, that.bundleId) && Objects.equals(applicationId, that.applicationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOrgId(), bundleId, applicationId);
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
