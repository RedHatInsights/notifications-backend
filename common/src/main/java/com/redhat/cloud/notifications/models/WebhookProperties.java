package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "endpoint_webhooks")
@JsonNaming(SnakeCaseStrategy.class)
public class WebhookProperties extends EndpointProperties implements SourcesSecretable {
    @NotNull
    private String url;

    @NotNull
    @Convert(converter = HttpTypeConverter.class)
    private HttpType method;

    @NotNull
    @JsonProperty("disable_ssl_verification")
    private Boolean disableSslVerification = Boolean.FALSE;

    @Size(max = 255)
    @JsonProperty("secret_token")
    private String secretToken; // TODO Should be optional

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore
    private long secretTokenSourcesId;

    @Convert(converter = BasicAuthenticationConverter.class)
    @JsonProperty("basic_authentication")
    private BasicAuthentication basicAuthentication;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    @JsonIgnore
    private long basicAuthenticationSourcesId;

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

    public long getSecretTokenSourcesId() {
        return secretTokenSourcesId;
    }

    public void setSecretTokenSourcesId(long secretTokenSourcesId) {
        this.secretTokenSourcesId = secretTokenSourcesId;
    }

    public BasicAuthentication getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    public long getBasicAuthenticationSourcesId() {
        return basicAuthenticationSourcesId;
    }

    public void setBasicAuthenticationSourcesId(long basicAuthenticationSourcesId) {
        this.basicAuthenticationSourcesId = basicAuthenticationSourcesId;
    }
}
