package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "endpoint_webhooks")
public class WebhookAttributes extends Attributes {

    public enum HttpType {
        GET, POST;
    }

    @Id
    private Integer id;

    private String url;
    private HttpType method; // Should be something typed

    @Column(name = "disable_ssl_verification")
    @JsonProperty("disable_ssl_verification")
    private boolean disableSSLVerification;

    @Column(name = "secret_token")
    @JsonProperty("secret_token")
    private String secretToken;

    public WebhookAttributes() {
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

    public String getSecretToken() {
        return secretToken;
    }
}
