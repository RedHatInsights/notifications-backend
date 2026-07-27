package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class BundleSubscriptionUpdateDTO {

    @NotBlank
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    private String bundle;

    @NotEmpty
    @Valid
    private List<@NotNull ApplicationSubscriptionUpdateDTO> applications;

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public List<ApplicationSubscriptionUpdateDTO> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationSubscriptionUpdateDTO> applications) {
        this.applications = applications;
    }
}
