package com.redhat.cloud.notifications.recipients.resolver.rbac;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import java.util.Map;

public class AuthRequestFilter implements ClientRequestFilter {

    static final String RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY = "rbac.service-to-service.application";
    static final String RBAC_SERVICE_TO_SERVICE_APPLICATION_DEFAULT = "notifications";

    static final String RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY = "rbac.service-to-service.secret-map";
    static final String RBAC_SERVICE_TO_SERVICE_SECRET_MAP_DEFAULT = "{}";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Secret {
        public String secret;

        @JsonProperty("alt-secret")
        public String altSecret;
    }

    private String secret;
    private final String application;

    AuthRequestFilter() {
        Config config = ConfigProvider.getConfig();

        application = config.getOptionalValue(RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY, String.class).orElse(RBAC_SERVICE_TO_SERVICE_APPLICATION_DEFAULT);
        Map<String, Secret> rbacServiceToServiceSecretMap;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            rbacServiceToServiceSecretMap = objectMapper.readValue(
                config.getOptionalValue(RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY, String.class).orElse(RBAC_SERVICE_TO_SERVICE_SECRET_MAP_DEFAULT),
                new TypeReference<>() { }
            );
        } catch (JsonProcessingException jsonProcessingException) {
            Log.error("Unable to load Rbac service to service secret map, defaulting to empty map", jsonProcessingException);
            rbacServiceToServiceSecretMap = Map.of();
        }

        secret = rbacServiceToServiceSecretMap.getOrDefault(application, new Secret()).secret;
        if (secret == null) {
            Log.error("Unable to load Rbac service to service secret key, trying to use the alt-secret instead");
            secret = rbacServiceToServiceSecretMap.getOrDefault(application, new Secret()).altSecret;
            if (secret == null) {
                Log.error("Unable to load Rbac service to service alt-secret key");
            }
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle("x-rh-rbac-psk", secret);
        requestContext.getHeaders().putSingle("x-rh-rbac-client-id", application);
    }

    public String getSecret() {
        return secret;
    }

    public String getApplication() {
        return application;
    }
}
