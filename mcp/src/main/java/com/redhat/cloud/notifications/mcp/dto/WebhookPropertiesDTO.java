package com.redhat.cloud.notifications.mcp.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Properties for webhook and ansible endpoint types.
 *
 * Based on: backend/src/main/java/com/redhat/cloud/notifications/models/dto/v1/endpoint/properties/WebhookPropertiesDTO.java
 *
 * Note: The backend includes an @AssertTrue validation that only POST method is allowed.
 * MCP layer omits this validation to keep DTOs simple - backend will validate on receive.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@RegisterForReflection
public class WebhookPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    @NotNull
    private String method = "POST";

    @NotNull
    private String url;

    private String bearerAuthentication;

    @Size(max = 255)
    private String secretToken;

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBearerAuthentication() {
        return bearerAuthentication;
    }

    public void setBearerAuthentication(String bearerAuthentication) {
        this.bearerAuthentication = bearerAuthentication;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }
}
