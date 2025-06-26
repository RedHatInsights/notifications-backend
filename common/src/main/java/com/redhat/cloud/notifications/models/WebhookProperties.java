package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class) // TODO remove them once the transition to DTOs have been completed.
@Table(name = "endpoint_webhooks")
public class WebhookProperties extends EndpointProperties implements SourcesSecretable {

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    @Convert(converter = HttpTypeConverter.class)
    @NotNull
    private HttpType method;

    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    @Size(max = 255)
    @Transient
    private String secretToken;

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore // TODO remove them once the transition to DTOs have been completed.
    private Long secretTokenSourcesId;

    @Column(name = "bearer_authentication_id")
    @JsonIgnore // TODO remove them once the transition to DTOs have been completed.
    private Long bearerAuthenticationSourcesId;

    @Transient
    private String bearerAuthentication;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HttpType getMethod() {
        return method;
    }

    public void setMethod(HttpType method) {
        this.method = method;
    }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public Long getSecretTokenSourcesId() {
        return secretTokenSourcesId;
    }

    public void setSecretTokenSourcesId(Long secretTokenSourcesId) {
        this.secretTokenSourcesId = secretTokenSourcesId;
    }

    public BasicAuthentication getBasicAuthentication() {
        return null;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        // do nothing
    }

    public Long getBasicAuthenticationSourcesId() {
        return null;
    }

    public void setBasicAuthenticationSourcesId(Long basicAuthenticationSourcesId) {
        // do nothing
    }

    public Long getBearerAuthenticationSourcesId() {
        return bearerAuthenticationSourcesId;
    }

    public void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId) {
        this.bearerAuthenticationSourcesId = bearerAuthenticationSourcesId;
    }

    public String getBearerAuthentication() {
        return bearerAuthentication;
    }

    public void setBearerAuthentication(String bearerAuthentication) {
        this.bearerAuthentication = bearerAuthentication;
    }
}
