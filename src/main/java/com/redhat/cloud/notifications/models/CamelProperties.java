package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "endpoint_camel")
@JsonNaming(SnakeCaseStrategy.class)
public class CamelProperties extends EndpointProperties {

    @NotNull
    private String url;

    @NotNull
    @JsonProperty("disable_ssl_verification")
    @Column(name = "disable_ssl_verification")
    private Boolean disableSslVerification = Boolean.FALSE;

    @Size(max = 255)
    @JsonProperty("secret_token")
    private String secretToken; // TODO Should be optional

    // TODO we should basic-auth encode this when receiving and then store in encoded form only.
    //      likewise for the webhooks case.
    @Convert(converter = BasicAuthenticationConverter.class)
    @JsonProperty("basic_authentication")
    private BasicAuthentication basicAuthentication;

    // Subtype for camel
    @JsonProperty("sub_type")
    @Column(name = "sub_type")
    private String subtype;

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

    public BasicAuthentication getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    @Override
    public String toString() {
        return "CamelAttributes{" +
                "subType=" + subtype +
                ", url='" + url + '\'' +
                ", disableSSLVerification=" + disableSslVerification +
                ", secretToken='" + secretToken + '\'' +
                '}';
    }

    public void setExtras(Map<String, String> extras) {
        this.extras = extras;
    }

    public Map<String, String> getExtras() {
        return extras;
    }
}
