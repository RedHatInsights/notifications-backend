package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Entity
@Table(name = "camel_properties")
@JsonNaming(SnakeCaseStrategy.class)
public class CamelProperties extends EndpointProperties implements SourcesSecretable {

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    @Size(max = 255)
    private String secretToken; // TODO Should be optional

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore
    private Long secretTokenSourcesId;

    @Convert(converter = BasicAuthenticationConverter.class)
    @Valid
    private BasicAuthentication basicAuthentication;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    @JsonIgnore
    private Long basicAuthenticationSourcesId;

    @Convert(converter = MapConverter.class)
    private Map<String, String> extras;

    public String getUrl() {
        return url;
    }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public String getSecretToken() {
        return secretToken;
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
        return null;
    }

    public void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId) {
        // do nothing here
    }

    public String getBearerAuthentication() {
        return null;
    }

    public void setBearerAuthentication(String bearerAuthentication) {
        // do nothing here
    }

    @Override
    public String toString() {
        return "CamelProperties{" +
                ", url='" + url + '\'' +
                ", disableSSLVerification=" + disableSslVerification +
                '}';
    }

    public void setExtras(Map<String, String> extras) {
        this.extras = extras;
    }

    public Map<String, String> getExtras() {
        return extras;
    }
}
