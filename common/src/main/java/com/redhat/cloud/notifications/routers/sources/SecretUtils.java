package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.BasicAuthenticationLegacy;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class SecretUtils {

    /**
     * Used to gather data regarding the number of times that Sources gets
     * called.
     */
    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    /**
     * Used to manage the secrets on Sources.
     */
    @Inject
    @RestClient
    SourcesService sourcesService;

    private static final String SOURCES_TIMER = "sources.get.secret.request";

    /**
     * Loads the endpoint's secrets from Sources.
     * @param endpoint the endpoint to get the secrets from.
     */
    public void loadSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long basicAuthSourcesId = props.getBasicAuthenticationSourcesId();
            if (basicAuthSourcesId != null) {
                Secret secret = loadSecretFromSources(endpoint, basicAuthSourcesId);

                props.setBasicAuthenticationLegacy(
                    new BasicAuthenticationLegacy(
                        secret.username,
                        secret.password
                    )
                );
            }

            final Long secretTokenSourcesId = props.getSecretTokenSourcesId();
            if (secretTokenSourcesId != null) {
                Secret secret = loadSecretFromSources(endpoint, secretTokenSourcesId);
                props.setSecretTokenLegacy(secret.password);
            }

            final Long bearerSourcesId = props.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                Secret secret = loadSecretFromSources(endpoint, bearerSourcesId);
                props.setBearerAuthenticationLegacy(secret.password);
            }
        }
    }

    private Secret loadSecretFromSources(Endpoint endpoint, Long secretId) {

        final Timer.Sample getSecretTimer = Timer.start(this.meterRegistry);

        final Secret secret = this.sourcesService.getById(
            endpoint.getOrgId(),
            this.sourcesPsk,
            secretId
        );

        getSecretTimer.stop(this.meterRegistry.timer(SOURCES_TIMER));
        return secret;
    }

    /**
     * Creates the endpoint's secrets in Sources.
     * @param endpoint the endpoint to create the secrets from.
     */
    public void createSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final BasicAuthenticationLegacy basicAuth = props.getBasicAuthenticationLegacy();
            if (!this.isBasicAuthNullOrBlank(basicAuth)) {
                final long id = this.createBasicAuthentication(basicAuth, endpoint.getOrgId());

                Log.infof("[secret_id: %s] Basic authentication secret created in Sources", id);

                props.setBasicAuthenticationSourcesId(id);
            }

            final String secretToken = props.getSecretTokenLegacy();
            if (secretToken != null && !secretToken.isBlank()) {
                final long id = this.createSecretTokenSecret(secretToken, Secret.TYPE_SECRET_TOKEN, endpoint.getOrgId());

                Log.infof("[secret_id: %s] Secret token secret created in Sources", id);

                props.setSecretTokenSourcesId(id);
            }

            final String bearerToken = props.getBearerAuthenticationLegacy();
            if (bearerToken != null && !bearerToken.isBlank()) {
                final long id = this.createSecretTokenSecret(secretToken, Secret.TYPE_BEARER_AUTHENTICATION, endpoint.getOrgId());
                Log.infof("[secret_id: %s] Secret bearer token created in Sources", id);
                props.setBearerAuthenticationSourcesId(id);
            }
        }
    }

    /**
     * <p>Updates the endpoint's secrets in Sources. However a few cases are covered for the secrets:</p>
     * <ul>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret is {@code null}, it is assumed that the
     *  user wants the secret to be deleted.</li>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret isn't {@code null}, then the secret is
     *  updated.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret is {@code null}, it's basically
     *  a NOP — although the attempt is logged for debugging purposes.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret isn't {@code null}, it is
     *  assumed that the user wants the secret to be created.</li>
     * </ul>
     * @param endpoint the endpoint to update the secrets from.
     */
    public void updateSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final BasicAuthenticationLegacy basicAuth = props.getBasicAuthenticationLegacy();
            final Long basicAuthId = props.getBasicAuthenticationSourcesId();
            if (basicAuthId != null) {
                if (this.isBasicAuthNullOrBlank(basicAuth)) {
                    deleteSecret(endpoint, basicAuthId, "[endpoint_id: %s][secret_id: %s] Basic authentication secret deleted in Sources during an endpoint update operation");

                    props.setBasicAuthenticationSourcesId(null);
                } else {
                    Secret secret = new Secret();

                    secret.password = basicAuth.getPassword();
                    secret.username = basicAuth.getUsername();

                    this.sourcesService.update(
                        endpoint.getOrgId(),
                        this.sourcesPsk,
                        basicAuthId,
                        secret
                    );
                    Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources during an endpoint update operation", endpoint.getId(), basicAuthId);
                }
            } else {
                if (this.isBasicAuthNullOrBlank(basicAuth)) {
                    Log.debugf("[endpoint_id: %s] Basic authentication secret not created in Sources: the basic authentication object is null", endpoint.getId());
                } else {
                    final long id = this.createBasicAuthentication(basicAuth, endpoint.getOrgId());
                    Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret created in Sources during an endpoint update operation", endpoint.getId(), id);

                    props.setBasicAuthenticationSourcesId(id);
                }
            }

            final String secretToken = props.getSecretTokenLegacy();
            final Long secretTokenId = props.getSecretTokenSourcesId();
            props.setSecretTokenSourcesId(updateSecretToken(endpoint, secretToken, secretTokenId, Secret.TYPE_SECRET_TOKEN, "Secret token secret"));

            final String bearerToken = props.getBearerAuthenticationLegacy();
            final Long bearerTokenId = props.getBearerAuthenticationSourcesId();
            props.setBearerAuthenticationSourcesId(updateSecretToken(endpoint, bearerToken, bearerTokenId, Secret.TYPE_BEARER_AUTHENTICATION, "Bearer token"));
        }
    }

    private Long updateSecretToken(Endpoint endpoint, String password, Long secretId, String secretType, String logDisplaySecretType) {
        if (secretId != null) {
            if (password == null || password.isBlank()) {
                this.sourcesService.delete(
                    endpoint.getOrgId(),
                    this.sourcesPsk,
                    secretId
                );
                Log.infof("[endpoint_id: %s][secret_id: %s] %s deleted in Sources during an endpoint update operation", endpoint.getId(), secretId, logDisplaySecretType);
                return null;
            } else {
                Secret secret = new Secret();
                secret.password = password;

                this.sourcesService.update(
                    endpoint.getOrgId(),
                    this.sourcesPsk,
                    secretId,
                    secret
                );
                Log.infof("[endpoint_id: %s][secret_id: %s] %s updated in Sources", endpoint.getId(), secretId, logDisplaySecretType);
                return secretId;
            }
        } else {
            if (password == null || password.isBlank()) {
                Log.debugf("[endpoint_id: %s] %s not created in Sources: the secret token object is null or blank", endpoint.getId(), logDisplaySecretType);
            } else {
                final long id = this.createSecretTokenSecret(password, secretType, endpoint.getOrgId());
                Log.infof("[endpoint_id: %s][secret_id: %s] %s created in Sources during an endpoint update operation", endpoint.getId(), id, logDisplaySecretType);
                return id;
            }
        }
        return secretId;
    }

    /**
     * Deletes the endpoint's secrets. It requires for the properties to have a "basic authentication" ID or "secret
     * token" ID on the database.
     * @param endpoint the endpoint to delete the secrets from.
     */
    public void deleteSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long basicAuthId = props.getBasicAuthenticationSourcesId();
            if (basicAuthId != null) {
                deleteSecret(endpoint, basicAuthId, "[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources");
            }

            final Long secretTokenId = props.getSecretTokenSourcesId();
            if (secretTokenId != null) {
                deleteSecret(endpoint, secretTokenId, "[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources");
            }

            final Long bearerSourcesId = props.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                deleteSecret(endpoint, bearerSourcesId, "[endpoint_id: %s][secret_id: %s] Bearer token deleted in Sources");
            }
        }
    }

    private void deleteSecret(Endpoint endpoint, Long secretId, String logMessageFormat) {
        this.sourcesService.delete(
            endpoint.getOrgId(),
            this.sourcesPsk,
            secretId
        );
        Log.infof(logMessageFormat, endpoint.getId(), secretId);
    }

    /**
     * Creates a "basic authentication" secret in Sources.
     * @param basicAuthenticationLegacy the contents of the "basic authentication" secret.
     * @param orgId the organization id related to this operation for the tenant identification.
     * @return the id of the created secret.
     */
    private long createBasicAuthentication(final BasicAuthenticationLegacy basicAuthenticationLegacy, final String orgId) {
        Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BASIC_AUTH;
        secret.password = basicAuthenticationLegacy.getPassword();
        secret.username = basicAuthenticationLegacy.getUsername();

        return createSecret(orgId, secret);
    }

    /**
     * Creates a "secret token" secret in Sources.
     * @param secretToken the "secret token"'s contents.
     * @param orgId the organization id related to this operation for the tenant identification.
     * @return the id of the created secret.
     */
    private long createSecretTokenSecret(final String secretToken, final String tokenType, final String orgId) {
        Secret secret = new Secret();

        secret.authenticationType = tokenType;
        secret.password = secretToken;

        return createSecret(orgId, secret);
    }

    private Long createSecret(String orgId, Secret secret) {
        final Secret createdSecret = this.sourcesService.create(
            orgId,
            this.sourcesPsk,
            secret
        );

        return createdSecret.id;
    }

    /**
     * Checks whether the provided {@link BasicAuthenticationLegacy} object is null, or if its inner password and username
     * fields are blank. If any of the username or password fields contain a non-blank string, then it is assumed that
     * the object is not blank.
     * @param basicAuthenticationLegacy the object to check.
     * @return {@code true} if the object is null, or if the password and the username are blank.
     */
    protected boolean isBasicAuthNullOrBlank(final BasicAuthenticationLegacy basicAuthenticationLegacy) {
        if (basicAuthenticationLegacy == null) {
            return true;
        }

        return (basicAuthenticationLegacy.getPassword() == null || basicAuthenticationLegacy.getPassword().isBlank()) &&
                (basicAuthenticationLegacy.getUsername() == null || basicAuthenticationLegacy.getUsername().isBlank());
    }
}
