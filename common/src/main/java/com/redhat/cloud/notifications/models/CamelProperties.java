package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.BasicAuthenticationConverter;
import com.redhat.cloud.notifications.db.converters.MapConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
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

    // TODO we should basic-auth encode this when receiving and then store in encoded form only.
    //      likewise for the webhooks case.
    @Convert(converter = BasicAuthenticationConverter.class)
    private BasicAuthentication basicAuthentication;

    // Todo: NOTIF-429 backward compatibility change - Remove soon.
    @Transient
    @Deprecated(forRemoval = true)
    private String subType;

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

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subtype) {
        this.subType = subtype;
    }

    @Override
    public String toString() {
        return "CamelProperties{" +
                "subType=" + subType +
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
