package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.secrets.BasicAuthenticationDTO;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public final class CamelPropertiesDTO extends EndpointPropertiesDTO {
    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    private Map<String, String> extras;

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    @Valid
    private BasicAuthenticationDTO basicAuthentication;

    @Size(max = 255)
    private String secretToken;

    public CamelPropertiesDTO() { }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(final Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public void setExtras(final Map<String, String> extras) {
        this.extras = extras;
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

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }
}
