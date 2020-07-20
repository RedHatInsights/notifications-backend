package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookAttributes extends Attributes {

    public enum HttpType {
        GET, POST;
    }

    // TODO Validation for these properties (to prevent errors when inserting)

    @JsonIgnore
    private Integer id;

    private String url;
    private HttpType method;

    @JsonProperty("disable_ssl_verification")
    private boolean disableSSLVerification = false;

    @JsonProperty("secret_token")
    private String secretToken; // TODO Should be optional

    public WebhookAttributes() {
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public HttpType getMethod() {
        return method;
    }

    public boolean isDisableSSLVerification() {
        return disableSSLVerification;
    }

    public void setDisableSSLVerification(boolean disableSSLVerification) {
        this.disableSSLVerification = disableSSLVerification;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(HttpType method) {
        this.method = method;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public String getSecretToken() {
        return secretToken;
    }

    @Override
    public String toString() {
        return "WebhookAttributes{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", method=" + method +
                ", disableSSLVerification=" + disableSSLVerification +
                ", secretToken='" + secretToken + '\'' +
                '}';
    }
}
