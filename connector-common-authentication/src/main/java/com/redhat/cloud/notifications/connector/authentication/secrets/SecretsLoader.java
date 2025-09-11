package com.redhat.cloud.notifications.connector.authentication.secrets;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Processor for loading secrets from external services.
 * Updated for Quarkus implementation without Camel dependencies.
 */
@ApplicationScoped
public class SecretsLoader {

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "notifications.connector.secrets.service.url", defaultValue = "http://localhost:8080")
    String secretsServiceUrl;

    /**
     * Process secrets loading for a given payload.
     * This replaces the Camel-based process method.
     */
    public JsonObject process(JsonObject payload) {
        if (payload == null) {
            return new JsonObject();
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Log.debugf("Loading secrets for orgId: %s", payload.getString("org_id"));

            // Extract secret ID from payload
            String secretId = payload.getString("secret_id");
            if (secretId == null || secretId.isEmpty()) {
                Log.debug("No secret ID found, returning original payload");
                return payload;
            }

            // Load secrets from external service
            JsonObject secrets = loadSecretsFromService(secretId);
            if (secrets != null && !secrets.isEmpty()) {
                // Merge secrets into payload
                JsonObject result = payload.copy();
                result.put("secret_username", secrets.getString("username"));
                result.put("secret_password", secrets.getString("password"));
                return result;
            }

            return payload;

        } catch (Exception e) {
            Log.errorf(e, "Error loading secrets: %s", e.getMessage());
            return payload;
        } finally {
            sample.stop(Timer.builder("notifications.connector.secrets.load.duration")
                .tag("connector", connectorConfig.getConnectorName())
                .register(meterRegistry));
        }
    }

    /**
     * Load secrets from external service.
     */
    private JsonObject loadSecretsFromService(String secretId) {
        try {
            // In a real implementation, this would call the secrets service
            // For now, return a mock response
            Log.debugf("Loading secrets for secretId: %s", secretId);

            JsonObject mockSecrets = new JsonObject();
            mockSecrets.put("username", "mock-username");
            mockSecrets.put("password", "mock-password");

            return mockSecrets;
        } catch (Exception e) {
            Log.errorf(e, "Failed to load secrets for secretId: %s", secretId);
            return null;
        }
    }
}
