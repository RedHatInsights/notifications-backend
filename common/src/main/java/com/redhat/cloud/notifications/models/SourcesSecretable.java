package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.models.secrets.BasicAuthentication;
import com.redhat.cloud.notifications.models.secrets.BearerToken;
import com.redhat.cloud.notifications.models.secrets.SecretToken;

/**
 * The goal of this interface is to declare the required getters and setters the target class needs to implement. This
 * will enable the class to be able to store the secrets in Sources, and to pull the secrets from it.
 */
public interface SourcesSecretable {
    Secrets getSecrets();

    BasicAuthentication getBasicAuthentication();

    void setBasicAuthentication(BasicAuthentication basicAuthenticationLegacy);

    BearerToken getBearerToken();

    void setBearerToken(BearerToken bearerToken);

    SecretToken getSecretToken();

    void setSecretToken(SecretToken secretToken);

    /**
     * Get the contents of the secret token.
     * @return the contents of the secret token.
     */
    @Deprecated(forRemoval = true)
    String getSecretTokenLegacy();

    /**
     * Set the contents of the secret token.
     * @param secretToken the contents of the secret token.
     */
    @Deprecated(forRemoval = true)
    void setSecretTokenLegacy(String secretToken);

    /**
     * Get the ID of the "secret token" secret stored in Sources.
     * @return the ID of the secret.
     */
    Long getSecretTokenSourcesId();

    /**
     * Set the ID of the "secret token" secret stored in Sources.
     * @param secretTokenSourcesId the ID of the secret.
     */
    void setSecretTokenSourcesId(Long secretTokenSourcesId);

    /**
     * Get the basic authentication object.
     * @return the basic authentication object.
     */
    @Deprecated(forRemoval = true)
    BasicAuthenticationLegacy getBasicAuthenticationLegacy();

    /**
     * Set the basic authentication object.
     * @param basicAuthenticationLegacy the basic authentication object to be set.
     */
    @Deprecated(forRemoval = true)
    void setBasicAuthenticationLegacy(BasicAuthenticationLegacy basicAuthenticationLegacy);


    /**
     * Get the ID of the "basic authentication" secret stored in Sources.
     * @return the ID of the secret.
     */
    Long getBasicAuthenticationSourcesId();

    /**
     * Set the ID of the "basic authentication" secret stored in Sources.
     * @param basicAuthenticationSourcesId the ID of the secret.
     */
    void setBasicAuthenticationSourcesId(Long basicAuthenticationSourcesId);

    Long getBearerAuthenticationSourcesId();

    void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId);

    @Deprecated(forRemoval = true)
    String getBearerAuthenticationLegacy();

    @Deprecated(forRemoval = true)
    void setBearerAuthenticationLegacy(String bearerAuthentication);
}
