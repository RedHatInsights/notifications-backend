package com.redhat.cloud.notifications.connector.authentication.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesOidcClient;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesPskClient;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResult;
import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.Optional;

@ApplicationScoped
public class AuthenticationLoader {

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

    @Inject
    ObjectMapper objectMapper;

    public Optional<AuthenticationResult> fetchAuthenticationData(String orgId, JsonObject authenticationData) {
        if (authenticationData == null) {
            return Optional.empty();
        }
        AuthenticationRequest secretRequest = objectMapper.convertValue(authenticationData, AuthenticationRequest.class);
        validate(secretRequest);

        Log.debugf("Calling Sources to retrieve a secret [orgId=%s, secretId=%d]", orgId, secretRequest.secretId);

        Timer.Sample timer = Timer.start(meterRegistry);
        SourcesSecretResult sourcesSecretResult;

        if (connectorConfig.isSourcesOidcAuthEnabled(orgId)) {
            Log.debug("Using OIDC Sources client");
            sourcesSecretResult = sourcesOidcClient.getById(orgId, secretRequest.secretId);
        } else {
            Log.debug("Using PSK Sources client");
            sourcesSecretResult = sourcesPskClient.getById(orgId, sourcesApiPsk, secretRequest.secretId);
        }
        timer.stop(meterRegistry.timer(SOURCES_TIMER));

        if (sourcesSecretResult.username != null && !sourcesSecretResult.username.isBlank()) {
            Log.debug("Found a secret username in the response from Sources");
        }

        if (sourcesSecretResult.password != null && !sourcesSecretResult.password.isBlank()) {
            Log.debug("Found a secret password in the response from Sources");
        }
        return Optional.of(new AuthenticationResult(sourcesSecretResult, secretRequest.authenticationType));
    }

    static void validate(AuthenticationRequest secretRequest) {
        // If the authentication data is available, an exception is thrown if anything is wrong with it.
        if (secretRequest.authenticationType == null) {
            throw new IllegalStateException("Invalid payload: the authentication type is missing or unknown");
        }

        if (secretRequest.secretId == null) {
            throw new IllegalStateException("Invalid payload: the secret ID is missing");
        }
    }
}
