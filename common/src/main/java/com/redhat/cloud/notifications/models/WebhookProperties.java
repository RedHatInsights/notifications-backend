package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "endpoint_webhooks")
@JsonNaming(SnakeCaseStrategy.class)
public class WebhookProperties extends EndpointProperties implements SourcesSecretable {

    @NotNull
    @ValidNonPrivateUrl
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
    private Long secretTokenSourcesId;

    @Convert(converter = BasicAuthenticationConverter.class)
    @JsonProperty("basic_authentication")
    @Valid
    private BasicAuthentication basicAuthentication;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    @JsonIgnore
    private Long basicAuthenticationSourcesId;

    @Column(name = "bearer_authentication_id")
    @JsonIgnore
    private Long bearerAuthenticationSourcesId;

    @JsonProperty("bearer_authentication")
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
        return basicAuthentication;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    public Long getBasicAuthenticationSourcesId() {
        return basicAuthenticationSourcesId;
    }

    public void setBasicAuthenticationSourcesId(Long basicAuthenticationSourcesId) {
        this.basicAuthenticationSourcesId = basicAuthenticationSourcesId;
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
