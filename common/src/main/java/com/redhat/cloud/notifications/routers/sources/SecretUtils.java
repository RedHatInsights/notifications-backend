package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.BasicAuthenticationLegacy;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.secrets.BasicAuthentication;
import com.redhat.cloud.notifications.models.secrets.BearerToken;
import com.redhat.cloud.notifications.models.secrets.SecretToken;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

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
        final EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable properties) {

            final Long basicAuthSourcesId = properties.getBasicAuthenticationSourcesId();
            if (basicAuthSourcesId != null) {
                final Secret secret = this.loadSecretFromSources(endpoint.getOrgId(), basicAuthSourcesId);

                properties.setBasicAuthenticationLegacy(
                    new com.redhat.cloud.notifications.models.BasicAuthenticationLegacy(
                        secret.username,
                        secret.password
                    )
                );

                properties.setBasicAuthentication(
                    new BasicAuthentication(
                        secret.username,
                        secret.password
                    )
                );
            }

            final Long secretTokenSourcesId = properties.getSecretTokenSourcesId();
            if (secretTokenSourcesId != null) {
                final Secret secret = this.loadSecretFromSources(endpoint.getOrgId(), secretTokenSourcesId);
                properties.setSecretTokenLegacy(secret.password);
                properties.setSecretToken(new SecretToken(secret.password));
            }

            final Long bearerSourcesId = properties.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                final Secret secret = this.loadSecretFromSources(endpoint.getOrgId(), bearerSourcesId);
                properties.setBearerAuthenticationLegacy(secret.password);
                properties.setBearerToken(new BearerToken(secret.password));
            }
        }
    }

    /**
     * Loads a secret from Sources.
     * @param orgId the organization ID the secret is associated to.
     * @param secretId the secret's Sources ID.
     * @return the stored secret in Sources.
     */
    private Secret loadSecretFromSources(final String orgId, final Long secretId) {
        final Timer.Sample getSecretTimer = Timer.start(this.meterRegistry);

        final Secret secret = this.sourcesService.getById(
            orgId,
            this.sourcesPsk,
            secretId
        );

        getSecretTimer.stop(this.meterRegistry.timer(SOURCES_TIMER));
        return secret;
    }

    /**
     * Creates the endpoint's secrets in Sources.
     * @param endpoint the endpoint to create the secrets from.
     * @deprecated since it will be deleted once the Sources migration service
     * is deleted.
     */
    @Deprecated(forRemoval = true)
    public void createSecretsForEndpointLegacy(Endpoint endpoint) {
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
                final long id = this.createSecretTokenSecretLegacy(secretToken, Secret.TYPE_SECRET_TOKEN, endpoint.getOrgId());

                Log.infof("[secret_id: %s] Secret token secret created in Sources", id);

                props.setSecretTokenSourcesId(id);
            }

            final String bearerToken = props.getBearerAuthenticationLegacy();
            if (bearerToken != null && !bearerToken.isBlank()) {
                final long id = this.createSecretTokenSecretLegacy(secretToken, Secret.TYPE_BEARER_AUTHENTICATION, endpoint.getOrgId());
                Log.infof("[secret_id: %s] Secret bearer token created in Sources", id);
                props.setBearerAuthenticationSourcesId(id);
            }
        }
    }

    /**
     * Creates the endpoint's secrets in Sources.
     * @param endpoint the endpoint to create the secrets from.
     */
    public void createSecretsForEndpoint(Endpoint endpoint) {
        final EndpointProperties endpointProperties = endpoint.getProperties();
        // Synchronize the legacy and the new properties, since we are going to
        // work with just the new ones.
        this.legacySyncProperties(endpointProperties);

        if (endpointProperties instanceof SourcesSecretable properties) {

            final BasicAuthentication basicAuth = properties.getBasicAuthentication();
            if (!this.isBasicAuthenticationNullOrBlank(basicAuth)) {
                final long id = this.createBasicAuthenticationSecret(endpoint.getOrgId(), basicAuth);

                Log.infof("[secret_id: %s] Basic authentication secret created in Sources", id);

                properties.setBasicAuthenticationSourcesId(id);
            }

            final SecretToken secretToken = properties.getSecretToken();
            if (secretToken != null && !secretToken.isBlank()) {
                final long id = this.createSecretTokenSecret(endpoint.getOrgId(), secretToken);

                Log.infof("[secret_id: %s] Secret token secret created in Sources", id);

                properties.setSecretTokenSourcesId(id);
            }

            final BearerToken bearerToken = properties.getBearerToken();
            if (bearerToken != null && !bearerToken.isBlank()) {
                final long id = this.createBearerTokenSecret(endpoint.getOrgId(), bearerToken);

                Log.infof("[secret_id: %s] Secret bearer token created in Sources", id);

                properties.setBearerAuthenticationSourcesId(id);
            }
        }
    }

    /**
     * Creates a basic authentication secret for the given endpoint. If the
     * endpoint already had one associated, it updates it instead.
     * @param endpoint the endpoint to create or update the basic
     *                 authentication secret for.
     * @param basicAuthentication the basic authentication to be created or
     *                            updated.
     */
    public void createUpdateBasicAuthenticationSecret(final Endpoint endpoint, final BasicAuthentication basicAuthentication) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long basicAuthenticationSourcesReference = properties.getBasicAuthenticationSourcesId();
        if (basicAuthenticationSourcesReference == null) {
            final long secretId = this.createBasicAuthenticationSecret(endpoint.getOrgId(), basicAuthentication);

            Log.infof("[secret_id: %s] Basic authentication secret created in Sources", secretId);

            properties.setBasicAuthenticationSourcesId(secretId);
        } else {
            this.updateBasicAuthenticationSecret(endpoint.getOrgId(), basicAuthenticationSourcesReference, basicAuthentication);

            Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources", endpoint.getId(), basicAuthenticationSourcesReference);
        }
    }

    /**
     * Deletes the basic authentication secret from Sources for the given
     * endpoint.
     * @param endpoint the endpoint to delete the secret for.
     */
    public void deleteBasicAuthenticationSecret(final Endpoint endpoint) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long basicAuthenticationSourcesReference = properties.getBasicAuthenticationSourcesId();
        if (basicAuthenticationSourcesReference != null) {
            this.deleteSecret(endpoint, basicAuthenticationSourcesReference, "[endpoint_id: %s][secret_id: %s] Basic authentication secret deleted in Sources");

            properties.setBasicAuthenticationSourcesId(null);
        }
    }

    /**
     * Creates a bearer token secret for the given endpoint. If the endpoint
     * already had one associated, it updates it instead.
     * @param endpoint the endpoint to create or update the bearer token secret
     *                 for.
     * @param bearerToken the bearer token to be created or updated.
     */
    public void createUpdateBearerTokenSecret(final Endpoint endpoint, final BearerToken bearerToken) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long bearerTokenSourcesReference = properties.getBearerAuthenticationSourcesId();
        if (bearerTokenSourcesReference == null) {
            final long secretId = this.createBearerTokenSecret(endpoint.getOrgId(), bearerToken);

            Log.infof("[secret_id: %s] Bearer token secret created in Sources", secretId);

            properties.setBearerAuthenticationSourcesId(secretId);
        } else {
            this.updateBearerTokenSecret(endpoint.getOrgId(), bearerTokenSourcesReference, bearerToken);

            Log.infof("[endpoint_id: %s][secret_id: %s] Bearer token secret updated in Sources", endpoint.getId(), bearerTokenSourcesReference);
        }
    }

    /**
     * Deletes the bearer token secret from Sources for the given endpoint.
     * @param endpoint the endpoint to delete the secret for.
     */
    public void deleteBearerTokenSecret(final Endpoint endpoint) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long bearerTokenSourcesReference = properties.getBearerAuthenticationSourcesId();
        if (bearerTokenSourcesReference != null) {
            this.deleteSecret(endpoint, bearerTokenSourcesReference, "[endpoint_id: %s][secret_id: %s] Bearer token secret deleted in Sources");

            properties.setBearerAuthenticationSourcesId(null);
        }
    }

    /**
     * Creates a secret token secret for the given endpoint. If the endpoint
     * already had one associated, it updates it instead.
     * @param endpoint the endpoint to create or update the secret token secret
     *                 for.
     * @param secretToken the bearer token to be created or updated.
     */
    public void createUpdateSecretTokenSecret(final Endpoint endpoint, final SecretToken secretToken) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long secretTokenSourcesReference = properties.getSecretTokenSourcesId();
        if (secretTokenSourcesReference == null) {
            final long secretId = this.createSecretTokenSecret(endpoint.getOrgId(), secretToken);

            Log.infof("[secret_id: %s] Secret token secret created in Sources", secretId);

            properties.setSecretTokenSourcesId(secretId);
        } else {
            this.updateSecretTokenSecret(endpoint.getOrgId(), secretTokenSourcesReference, secretToken);

            Log.infof("[endpoint_id: %s][secret_id: %s] Secret token secret updated in Sources", endpoint.getId(), secretTokenSourcesReference);
        }
    }

    /**
     * Deletes the secret token secret from Sources for the given endpoint.
     * @param endpoint the endpoint to delete the secret for.
     */
    public void deleteSecretTokenSecret(final Endpoint endpoint) {
        final SourcesSecretable properties = (SourcesSecretable) endpoint.getProperties();

        final Long secretTokenSourcesId = properties.getSecretTokenSourcesId();
        if (secretTokenSourcesId != null) {
            this.deleteSecret(endpoint, secretTokenSourcesId, "[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources");

            properties.setSecretTokenSourcesId(null);
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
     * @deprecated this is the legacy way of doing things. From now on, the
     * secrets will be updated with dedicated endpoints which define the clear
     * intent of what needs to be done, instead of us having to interpret what
     * we should do in each case.
     */
    @Deprecated(forRemoval = true)
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

    /**
     * @deprecated this is the legacy way of doing things. From now on, the
     * secrets will be updated with dedicated endpoints which define the clear
     * intent of what needs to be done, instead of us having to interpret what
     * we should do in each case.
     */
    @Deprecated(forRemoval = true)
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
                final long id = this.createSecretTokenSecretLegacy(password, secretType, endpoint.getOrgId());
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
    public void deleteSecretsForEndpoint(final Endpoint endpoint) {
        final EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable properties) {

            final Long basicAuthId = properties.getBasicAuthenticationSourcesId();
            if (basicAuthId != null) {
                this.deleteSecret(endpoint, basicAuthId, "[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources");
            }

            final Long secretTokenId = properties.getSecretTokenSourcesId();
            if (secretTokenId != null) {
                this.deleteSecret(endpoint, secretTokenId, "[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources");
            }

            final Long bearerSourcesId = properties.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                this.deleteSecret(endpoint, bearerSourcesId, "[endpoint_id: %s][secret_id: %s] Bearer token deleted in Sources");
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
     * @deprecated for removal since it works with the legacy version of the
     * basic authentication. Used in the {@link SecretUtils#updateSecretsForEndpoint(Endpoint)}
     * function.
     */
    @Deprecated(forRemoval = true)
    private long createBasicAuthentication(final BasicAuthenticationLegacy basicAuthenticationLegacy, final String orgId) {
        Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BASIC_AUTH;
        secret.password = basicAuthenticationLegacy.getPassword();
        secret.username = basicAuthenticationLegacy.getUsername();

        return createSecret(orgId, secret);
    }

    /**
     * Creates a "basic authentication" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param basicAuthentication the contents of the secret to create.
     * @return the id of the created secret.
     */
    private long createBasicAuthenticationSecret(final String orgId, final BasicAuthentication basicAuthentication) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BASIC_AUTH;
        secret.password = basicAuthentication.getPassword();
        secret.username = basicAuthentication.getUsername();

        return this.createSecret(orgId, secret);
    }

    /**
     * Updates a "basic authentication" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param secretId the ID of the secret to update.
     * @param basicAuthentication the contents of the secret to update.
     */
    private void updateBasicAuthenticationSecret(final String orgId, final long secretId, final BasicAuthentication basicAuthentication) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BASIC_AUTH;
        secret.password = basicAuthentication.getPassword();
        secret.username = basicAuthentication.getUsername();

        this.updateSecret(orgId, secretId, secret);
    }

    /**
     * Creates a "bearer token" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param bearerToken the contents of the secret to create.
     * @return the Sources ID reference of the created secret.
     */
    private long createBearerTokenSecret(final String orgId, final BearerToken bearerToken) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BEARER_AUTHENTICATION;
        secret.password = bearerToken.getToken();

        return this.createSecret(orgId, secret);
    }

    /**
     * Updates a "bearer token" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param secretId the ID of the secret to update.
     * @param bearerToken the contents of the secret to update.
     */
    private void updateBearerTokenSecret(final String orgId, final long secretId, final BearerToken bearerToken) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BEARER_AUTHENTICATION;
        secret.password = bearerToken.getToken();

        this.updateSecret(orgId, secretId, secret);
    }

    /**
     * Creates a "secret token" secret in Sources.
     * @param secretToken the "secret token"'s contents.
     * @param orgId the organization id related to this operation for the tenant identification.
     * @return the id of the created secret.
     * @deprecated for removal since it is only used in the legacy {@link SecretUtils#updateSecretsForEndpoint(Endpoint)}
     * function.
     */
    @Deprecated(forRemoval = true)
    private long createSecretTokenSecretLegacy(final String secretToken, final String tokenType, final String orgId) {
        Secret secret = new Secret();

        secret.authenticationType = tokenType;
        secret.password = secretToken;

        return createSecret(orgId, secret);
    }

    /**
     * Creates a "secret token" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param secretToken the contents of the secret to create.
     * @return the Sources ID reference of the created secret.
     */
    private long createSecretTokenSecret(final String orgId, final SecretToken secretToken) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_SECRET_TOKEN;
        secret.password = secretToken.getToken();

        return this.createSecret(orgId, secret);
    }

    /**
     * Updates a "secret token" secret in Sources.
     * @param orgId the organization the secret is associated with.
     * @param secretId the ID of the secret to update.
     * @param secretToken the contents of the secret to update.
     */
    private void updateSecretTokenSecret(final String orgId, final long secretId, final SecretToken secretToken) {
        final Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_SECRET_TOKEN;
        secret.password = secretToken.getToken();

        this.updateSecret(orgId, secretId, secret);
    }

    /**
     * Creates a Secret in sources.
     * @param orgId the organization to associate the secret with.
     * @param secret the secret to be created.
     * @return the reference ID for the Sources secret.
     */
    private Long createSecret(final String orgId, final Secret secret) {
        final Secret createdSecret = this.sourcesService.create(
            orgId,
            this.sourcesPsk,
            secret
        );

        return createdSecret.id;
    }

    /**
     * Updates a Secret in sources.
     * @param orgId the organization the secret belongs to.
     * @param secretId the ID of the secret to be updated.
     * @param secret the secret's contents to update.
     */
    private void updateSecret(final String orgId, final long secretId, final Secret secret) {
        this.sourcesService.update(
            orgId,
            this.sourcesPsk,
            secretId,
            secret
        );
    }

    /**
     * Checks whether the provide basic authentication object is null or blank.
     * @param basicAuthenticationLegacy the basic authentication object to check.
     * @return {@code true} if the basic authentication object is null or blank.
     */
    protected boolean isBasicAuthenticationNullOrBlank(final BasicAuthentication basicAuthenticationLegacy) {
        if (basicAuthenticationLegacy == null) {
            return true;
        } else {
            return basicAuthenticationLegacy.isBlank();
        }
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

    /**
     * Synchronizes the legacy properties and the new ones for the secrets. The
     * new properties take precedence in the event of both the legacy and new
     * properties are specified, and therefore the old ones get overridden.
     *
     * Basically, for all the secrets from an endpoint's properties:
     *
     * - If both the legacy and the new secrets are present, override the
     * legacy ones with the new ones.
     * - If only the legacy secrets are present, create the new secrets.
     * - If only the new secrets are present, create the legacy secrets.
     * @param endpointProperties the endpoint properties to update.
     * @deprecated since this function will only exist as long as we support
     * the old legacy properties.
     */
    @Deprecated(forRemoval = true)
    public void legacySyncProperties(final EndpointProperties endpointProperties) {
        final SourcesSecretable ss = (SourcesSecretable) endpointProperties;

        // Sync the basic authentication properties.
        final BasicAuthenticationLegacy basicAuthenticationLegacy = ss.getBasicAuthenticationLegacy();
        final Optional<BasicAuthentication> basicAuthenticationOptional = this.getBasicAuthentication(endpointProperties);

        if (basicAuthenticationLegacy != null && basicAuthenticationOptional.isPresent()) {
            final BasicAuthentication basicAuthentication = basicAuthenticationOptional.get();

            basicAuthenticationLegacy.setPassword(basicAuthentication.getPassword());
            basicAuthenticationLegacy.setUsername(basicAuthentication.getUsername());

            ss.setBasicAuthenticationLegacy(basicAuthenticationLegacy);
        }

        if (basicAuthenticationLegacy != null && basicAuthenticationOptional.isEmpty()) {
            final BasicAuthentication basicAuthentication = new BasicAuthentication(
                basicAuthenticationLegacy.getUsername(),
                basicAuthenticationLegacy.getPassword()
            );

            ss.setBasicAuthentication(basicAuthentication);
        }

        if (basicAuthenticationLegacy == null && basicAuthenticationOptional.isPresent()) {
            final BasicAuthentication basicAuthentication = basicAuthenticationOptional.get();

            final BasicAuthenticationLegacy bal = new BasicAuthenticationLegacy(
                basicAuthentication.getUsername(),
                basicAuthentication.getPassword()
            );

            ss.setBasicAuthenticationLegacy(bal);
        }

        // Sync the bearer token properties
        final String bearerTokenLegacy = ss.getBearerAuthenticationLegacy();
        final Optional<BearerToken> bearerTokenOptional = this.getBearerToken(endpointProperties);

        if (bearerTokenLegacy != null && bearerTokenOptional.isPresent()) {
            ss.setBearerAuthenticationLegacy(bearerTokenOptional.get().getToken());
        }

        if (bearerTokenLegacy != null && bearerTokenOptional.isEmpty()) {
            final BearerToken bearerToken = new BearerToken(
                bearerTokenLegacy
            );

            ss.setBearerToken(bearerToken);
        }

        if (bearerTokenLegacy == null && bearerTokenOptional.isPresent()) {
            final BearerToken bearerToken = bearerTokenOptional.get();

            ss.setBearerAuthenticationLegacy(bearerToken.getToken());
        }

        // Sync the secret token properties.
        final String secretTokenLegacy = ss.getSecretTokenLegacy();
        final Optional<SecretToken> secretTokenOptional = this.getSecretToken(endpointProperties);

        if (secretTokenLegacy != null && secretTokenOptional.isPresent()) {
            ss.setSecretTokenLegacy(secretTokenOptional.get().getToken());
        }

        if (secretTokenLegacy != null && secretTokenOptional.isEmpty()) {
            final SecretToken secretToken = new SecretToken(
                secretTokenLegacy
            );

            ss.setSecretToken(secretToken);
        }

        if (secretTokenLegacy == null && secretTokenOptional.isPresent()) {
            final SecretToken secretToken = secretTokenOptional.get();

            ss.setSecretTokenLegacy(secretToken.getToken());
        }
    }

    /**
     * Check if the given endpoint has properties that are of the type that
     * support Sources secrets.
     * @param endpoint the endpoint to check.
     * @return {@code true} if the endpoint's properties are from a class type
     * that supports Sources secrets.
     */
    public static boolean isSourcesSecretable(final Endpoint endpoint) {
        return endpoint.getProperties() instanceof CamelProperties
            || endpoint.getProperties() instanceof WebhookProperties;
    }

    /**
     * Gets the basic authentication object from the given endpoint properties.
     * @param endpointProperties the endpoint properties to get the basic
     *                           authentication from.
     * @return an optional basic authentication object.
     */
    private Optional<BasicAuthentication> getBasicAuthentication(final EndpointProperties endpointProperties) {
        if (endpointProperties instanceof CamelProperties cp) {
            return Optional.ofNullable(cp.getBasicAuthentication());
        } else if (endpointProperties instanceof WebhookProperties wp) {
            return Optional.ofNullable(wp.getBasicAuthentication());
        }

        return Optional.empty();
    }

    /**
     * Gets the bearer token object from the given endpoint properties.
     * @param endpointProperties the endpoint properties to get the bearer
     *                           token from.
     * @return an optional bearer token object.
     */
    private Optional<BearerToken> getBearerToken(final EndpointProperties endpointProperties) {
        if (endpointProperties instanceof CamelProperties cp) {
            return Optional.ofNullable(cp.getBearerToken());
        } else if (endpointProperties instanceof WebhookProperties wp) {
            return Optional.ofNullable(wp.getBearerToken());
        }

        return Optional.empty();
    }

    /**
     * Gets the secret token object from the given endpoint properties.
     * @param endpointProperties the endpoint properties to get the secret
     *                           token from.
     * @return an optional secret token object.
     */
    private Optional<SecretToken> getSecretToken(final EndpointProperties endpointProperties) {
        if (endpointProperties instanceof CamelProperties cp) {
            return Optional.ofNullable(cp.getSecretToken());
        } else if (endpointProperties instanceof WebhookProperties wp) {
            return Optional.ofNullable(wp.getSecretToken());
        }

        return Optional.empty();
    }
}
