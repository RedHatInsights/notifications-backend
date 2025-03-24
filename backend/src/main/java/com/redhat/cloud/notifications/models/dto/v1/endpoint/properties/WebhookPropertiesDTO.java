package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.secrets.BasicAuthenticationDTO;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WebhookPropertiesDTO extends EndpointPropertiesDTO {
    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    @NotNull
    private String method;

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    @Valid
    private BasicAuthenticationDTO basicAuthentication;

    private String bearerAuthentication;

    @Size(max = 255)
    private String secretToken;

    @JsonIgnore
    @AssertTrue(message = "Only \"POST\" methods are allowed for the properties of a webhook")
    private boolean isHttpMethodAllowed() {
        return HttpType.POST.name().equals(this.method);
    }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(final Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(final String method) {
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
