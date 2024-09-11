package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.redhat.cloud.notifications.models.PagerDutySeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * See {@link com.redhat.cloud.notifications.models.PagerDutyProperties} for default options
 */
public class PagerDutyPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    private PagerDutySeverity severity;

    @Size(max = 255)
    @NotNull
    private String secretToken;

    public PagerDutySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PagerDutySeverity severity) {
        this.severity = severity;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }
}
