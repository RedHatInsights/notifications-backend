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

    private String eventType;

    public EmailAggregationKey(String orgId, String bundle, String application, String eventType) {
        this.orgId = orgId;
        this.bundle = bundle;
        this.application = application;
        this.eventType = eventType;
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

    public String getEventType() {
        return eventType;
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
                Objects.equals(application, that.application) &&
                Objects.equals(eventType, this.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, bundle, application, eventType);
    }

    @Override
    public String toString() {
        return "EmailAggregationKey{" +
                "orgId='" + orgId + '\'' +
                ", bundle='" + bundle + '\'' +
                ", application='" + application + '\'' +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
