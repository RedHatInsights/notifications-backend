package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class BundleSubscriptionDTO {

    @NotNull
    private String bundle;

    @NotNull
    private String bundleDisplayName;

    @NotNull
    private List<ApplicationSubscriptionDTO> applications;

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getBundleDisplayName() {
        return bundleDisplayName;
    }

    public void setBundleDisplayName(String bundleDisplayName) {
        this.bundleDisplayName = bundleDisplayName;
    }

    public List<ApplicationSubscriptionDTO> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationSubscriptionDTO> applications) {
        this.applications = applications;
    }
}
