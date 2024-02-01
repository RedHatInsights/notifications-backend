package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.secrets.BasicAuthentication;
import com.redhat.cloud.notifications.models.secrets.BearerToken;
import com.redhat.cloud.notifications.models.secrets.SecretToken;

/**
 * Represents the secrets that the properties of the endpoints can support.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Secrets {
    private BasicAuthentication basicAuthentication;
    private BearerToken bearerToken;
    private SecretToken secretToken;

    public Secrets() { }

    public Secrets(final BasicAuthentication basicAuthentication, final BearerToken bearerToken, final SecretToken secretToken) {
        this.basicAuthentication = basicAuthentication;
        this.bearerToken = bearerToken;
        this.secretToken = secretToken;
    }

    public BasicAuthentication getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(final BasicAuthentication basicAuthenticationLegacy) {
        this.basicAuthentication = basicAuthenticationLegacy;
    }

    public BearerToken getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(final BearerToken bearerToken) {
        this.bearerToken = bearerToken;
    }

    public SecretToken getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final SecretToken secretToken) {
        this.secretToken = secretToken;
    }
}
