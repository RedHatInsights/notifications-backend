package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;

@Entity
@Table(name = "endpoint_webhooks")
public class WebhookProperties extends EndpointProperties implements SourcesSecretable {

    private String url;

    @Convert(converter = HttpTypeConverter.class)
    private HttpType method;

    private Boolean disableSslVerification = Boolean.FALSE;

    @Transient
    private String secretToken;

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    private Long secretTokenSourcesId;

    @Transient
    @Valid
    private BasicAuthentication basicAuthentication;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    private Long basicAuthenticationSourcesId;

    @Column(name = "bearer_authentication_id")
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
