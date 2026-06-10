package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Properties for camel endpoint types (Slack, Google Chat, MS Teams, ServiceNow, Splunk).
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/CamelPropertiesDTO.java
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@RegisterForReflection
public class CamelPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    private Map<String, String> extras;

    @NotNull
    private String url;

    @Size(max = 255)
    private String secretToken;

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public void setExtras(Map<String, String> extras) {
        this.extras = extras;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }
}
