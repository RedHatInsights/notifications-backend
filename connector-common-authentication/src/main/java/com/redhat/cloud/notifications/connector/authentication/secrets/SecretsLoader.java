package com.redhat.cloud.notifications.connector.authentication.secrets;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Loads secrets from the Sources service for authentication.
 * This is the new version that replaces the Camel-based SecretsLoader.
 */
@ApplicationScoped
public class SecretsLoader {

    private static final String SOURCES_API_PSK = "notifications.connector.authentication.secrets-loader.sources-api-psk";
    private static final String SOURCES_TIMER = "sources.get.secret.request";

    @ConfigProperty(name = SOURCES_API_PSK, defaultValue = "development-value-123")
    String sourcesApiPsk;

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    @RestClient
    SourcesPskClient sourcesPskClient;

    @Inject
    @RestClient
    SourcesOidcClient sourcesOidcClient;

    public Uni<Void> loadSecrets(ExceptionProcessor.ProcessingContext context) {
        Long secretId = context.getAdditionalProperty("SECRET_ID", Long.class);
        if (secretId == null) {
            // No secret ID, nothing to load
            return Uni.createFrom().voidItem();
        }

        String orgId = context.getOrgId();
        Log.debugf("Calling Sources to retrieve a secret [orgId=%s, secretId=%d]", orgId, secretId);

        Timer.Sample timer = Timer.start(meterRegistry);

        Uni<SourcesSecret> secretUni;
        if (connectorConfig.isSourcesOidcAuthEnabled(orgId)) {
            Log.debug("Using OIDC Sources client");
            secretUni = Uni.createFrom().item(() -> sourcesOidcClient.getById(orgId, secretId));
        } else {
            Log.debug("Using PSK Sources client");
            secretUni = Uni.createFrom().item(() -> sourcesPskClient.getById(orgId, sourcesApiPsk, secretId));
        }

        return secretUni
                .onItem().invoke(sourcesSecret -> {
                    timer.stop(meterRegistry.timer(SOURCES_TIMER));

                    if (sourcesSecret.username != null && !sourcesSecret.username.isBlank()) {
                        Log.debug("Found a secret username in the response from Sources");
                        context.setAdditionalProperty("SECRET_USERNAME", sourcesSecret.username);
                    }

                    if (sourcesSecret.password != null && !sourcesSecret.password.isBlank()) {
                        Log.debug("Found a secret password in the response from Sources");
                        context.setAdditionalProperty("SECRET_PASSWORD", sourcesSecret.password);
                    }
                })
                .onFailure().invoke(failure -> {
                    timer.stop(meterRegistry.timer(SOURCES_TIMER));
                    Log.errorf(failure, "Failed to load secret from Sources [orgId=%s, secretId=%d]", orgId, secretId);
                })
                .replaceWithVoid();
    }

    public Uni<SourcesSecret> getSecret(String orgId, Long secretId) {
        if (secretId == null) {
            return Uni.createFrom().nullItem();
        }

        Log.debugf("Calling Sources to retrieve a secret [orgId=%s, secretId=%d]", orgId, secretId);

        Timer.Sample timer = Timer.start(meterRegistry);

        Uni<SourcesSecret> secretUni;
        if (connectorConfig.isSourcesOidcAuthEnabled(orgId)) {
            Log.debug("Using OIDC Sources client");
            secretUni = Uni.createFrom().item(() -> sourcesOidcClient.getById(orgId, secretId));
        } else {
            Log.debug("Using PSK Sources client");
            secretUni = Uni.createFrom().item(() -> sourcesPskClient.getById(orgId, sourcesApiPsk, secretId));
        }

        return secretUni
                .onItem().invoke(secret -> timer.stop(meterRegistry.timer(SOURCES_TIMER)))
                .onFailure().invoke(failure -> {
                    timer.stop(meterRegistry.timer(SOURCES_TIMER));
                    Log.errorf(failure, "Failed to load secret from Sources [orgId=%s, secretId=%d]", orgId, secretId);
                });
    }
}
