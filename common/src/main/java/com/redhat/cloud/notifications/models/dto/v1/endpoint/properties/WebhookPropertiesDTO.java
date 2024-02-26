package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.secrets.BasicAuthenticationDTO;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WebhookPropertiesDTO extends EndpointPropertiesDTO {
    @NotNull
    @JsonProperty("disable_ssl_verification")
    private Boolean disableSslVerification = Boolean.FALSE;

    @NotNull
    private HttpType method;

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    @JsonProperty("basic_authentication")
    @Valid
    private BasicAuthenticationDTO basicAuthentication;

    @JsonProperty("bearer_authentication")
    private String bearerAuthentication;


    @Size(max = 255)
    @JsonProperty("secret_token")
    private String secretToken;

    public WebhookPropertiesDTO() { }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(final Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public HttpType getMethod() {
        return method;
    }

    public void setMethod(final HttpType method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public BasicAuthenticationDTO getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(final BasicAuthenticationDTO basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    public String getBearerAuthentication() {
        return bearerAuthentication;
    }

    public void setBearerAuthentication(final String bearerAuthentication) {
        this.bearerAuthentication = bearerAuthentication;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }
}
