package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class WebhookAttributes extends Attributes {

    @NotNull
    private String url;
    @NotNull
    private HttpType method;

    @JsonProperty("disable_ssl_verification")
    private boolean disableSSLVerification = false;

    @JsonProperty("secret_token")
    private String secretToken; // TODO Should be optional

    @JsonProperty("basic_authentication")
    private BasicAuthentication basicAuthentication;

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

    public BasicAuthentication getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    @Override
    public String toString() {
        return "WebhookAttributes{" +
                "url='" + url + '\'' +
                ", method=" + method +
                ", disableSSLVerification=" + disableSSLVerification +
                ", secretToken='" + secretToken + '\'' +
                '}';
    }
}
