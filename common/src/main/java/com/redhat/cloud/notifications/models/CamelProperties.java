package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.MapConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

@Entity
@Table(name = "camel_properties")
@JsonNaming(SnakeCaseStrategy.class)
public class CamelProperties extends EndpointProperties {

    @NotNull
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
    private long secretTokenSourcesId;

    @Convert(converter = BasicAuthenticationConverter.class)
    private BasicAuthentication basicAuthentication;

    /**
     * The ID of the "basic authentication" secret in the Sources backend.
     */
    @Column(name = "basic_authentication_id")
    @JsonIgnore
    private long basicAuthenticationSourcesId;

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
