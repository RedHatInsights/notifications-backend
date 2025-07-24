package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

public class EventAggregationCriterion {

    @NotNull
    private final UUID bundleId;

    @NotNull
    private final UUID applicationId;

    @NotNull
    private final String orgId;

    @NotNull
    private final String bundle;

    @NotNull
    private final String application;

    public String getOrgId() {
        return orgId;
    }

    public String getBundle() {
        return bundle;
    }

    public String getApplication() {
        return application;
    }

    public EventAggregationCriterion(String orgId, UUID bundleId, UUID applicationId, String bundle, String application) {
        this.orgId = orgId;
        this.bundle = bundle;
        this.application = application;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventAggregationCriterion that = (EventAggregationCriterion) o;
        return Objects.equals(bundleId, that.bundleId) && Objects.equals(applicationId, that.applicationId) && Objects.equals(orgId, that.orgId) && Objects.equals(bundle, that.bundle) && Objects.equals(application, that.application);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundleId, applicationId, getOrgId(), getBundle(), getApplication());
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
