package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * See {@link com.redhat.cloud.notifications.models.PagerDutyProperties} for default options
 */
public class PagerDutyPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    private PagerDutySeverityDTO severity;

    @Size(max = 32)
    @NotNull
    private String secretToken;

    public PagerDutySeverityDTO getSeverity() {
        return severity;
    }

    public void setSeverity(PagerDutySeverityDTO severity) {
        this.severity = severity;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }
}
