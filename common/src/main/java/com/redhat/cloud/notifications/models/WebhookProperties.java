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

    @Column(name = "secret_token")
    @Deprecated(forRemoval = true)
    @Size(max = 255)
    @JsonProperty("secret_token")
    private String secretTokenLegacy; // TODO Should be optional

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore
    private Long secretTokenSourcesId;

    @Column(name = "basic_authentication")
    @Convert(converter = BasicAuthenticationConverter.class)
    @Deprecated(forRemoval = true)
    @JsonProperty("basic_authentication")
    @Valid
    private BasicAuthenticationLegacy basicAuthenticationLegacy;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    @JsonIgnore
    private Long basicAuthenticationSourcesId;

    @Column(name = "bearer_authentication_id")
    @JsonIgnore
    private Long bearerAuthenticationSourcesId;

    @Column(name = "bearer_authentication")
    @Deprecated(forRemoval = true)
    @JsonProperty("bearer_authentication")
    private String bearerAuthenticationLegacy;

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

    @Deprecated(forRemoval = true)
    public String getSecretTokenLegacy() {
        return secretTokenLegacy;
    }

    @Deprecated(forRemoval = true)
    public void setSecretTokenLegacy(String secretToken) {
        this.secretTokenLegacy = secretToken;
    }

    public Long getSecretTokenSourcesId() {
        return secretTokenSourcesId;
    }

    public void setSecretTokenSourcesId(Long secretTokenSourcesId) {
        this.secretTokenSourcesId = secretTokenSourcesId;
    }

    @Deprecated(forRemoval = true)
    public BasicAuthenticationLegacy getBasicAuthenticationLegacy() {
        return basicAuthenticationLegacy;
    }

    @Deprecated(forRemoval = true)
    public void setBasicAuthenticationLegacy(BasicAuthenticationLegacy basicAuthenticationLegacy) {
        this.basicAuthenticationLegacy = basicAuthenticationLegacy;
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

    @Deprecated(forRemoval = true)
    public String getBearerAuthenticationLegacy() {
        return bearerAuthenticationLegacy;
    }

    @Deprecated(forRemoval = true)
    public void setBearerAuthenticationLegacy(String bearerAuthentication) {
        this.bearerAuthenticationLegacy = bearerAuthentication;
    }
}
