package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.MapConverter;
import com.redhat.cloud.notifications.models.secrets.BasicAuthentication;
import com.redhat.cloud.notifications.models.secrets.BearerToken;
import com.redhat.cloud.notifications.models.secrets.SecretToken;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    @Transient
    Secrets secrets = new Secrets();

    @Column(name = "secret_token")
    @Deprecated
    @Size(max = 255)
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

    @Deprecated(forRemoval = true)
    @JsonGetter("secret_token")
    public void setSecretTokenLegacy(String secretToken) {
        this.secretTokenLegacy = secretToken;
    }

    @Deprecated(forRemoval = true)
    @JsonSetter("secret_token")
    public String getSecretTokenLegacy() {
        return secretTokenLegacy;
    }

    public Long getSecretTokenSourcesId() {
        return secretTokenSourcesId;
    }

    public void setSecretTokenSourcesId(Long secretTokenSourcesId) {
        this.secretTokenSourcesId = secretTokenSourcesId;
    }

    @Deprecated(forRemoval = true)
    @JsonGetter("basic_authentication")
    public BasicAuthenticationLegacy getBasicAuthenticationLegacy() {
        return basicAuthenticationLegacy;
    }

    @Deprecated(forRemoval = true)
    @JsonSetter("basic_authentication")
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
        return null;
    }

    public void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId) {
        // do nothing here
    }

    @Deprecated(forRemoval = true)
    @JsonGetter("bearer_token")
    public String getBearerAuthenticationLegacy() {
        return null;
    }

    @Deprecated(forRemoval = true)
    @JsonSetter("bearer_token")
    public void setBearerAuthenticationLegacy(String bearerAuthentication) {
        // do nothing here
    }

    @Override
    public Secrets getSecrets() {
        return this.secrets;
    }

    @Override
    public BasicAuthentication getBasicAuthentication() {
        return this.secrets.getBasicAuthentication();
    }

    @Override
    public void setBasicAuthentication(final BasicAuthentication basicAuthentication) {
        this.secrets.setBasicAuthentication(basicAuthentication);
    }

    @Override
    public BearerToken getBearerToken() {
        // Not supported for Camel endpoints.
        return null;
    }

    @Override
    public void setBearerToken(final BearerToken bearerToken) {
        // Not supported for Camel endpoints.
    }

    @Override
    public SecretToken getSecretToken() {
        return this.secrets.getSecretToken();
    }

    @Override
    public void setSecretToken(final SecretToken secretToken) {
        this.secrets.setSecretToken(secretToken);
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
