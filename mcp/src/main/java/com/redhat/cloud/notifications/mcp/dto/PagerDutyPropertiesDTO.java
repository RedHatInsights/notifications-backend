package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Properties for PagerDuty endpoint type.
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/PagerDutyPropertiesDTO.java
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }
}
