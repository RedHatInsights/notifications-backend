package com.redhat.cloud.notifications.recipients.rbac;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logmanager.Level;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Logger;

public class AuthRequestFilter implements ClientRequestFilter {

    static final String RBAC_SERVICE_TO_SERVICE_APPLICATION_KEY = "rbac.service-to-service.application";
    static final String RBAC_SERVICE_TO_SERVICE_APPLICATION_DEFAULT = "notifications";

    static final String RBAC_SERVICE_TO_SERVICE_SECRET_MAP_KEY = "rbac.service-to-service.secret-map";
    static final String RBAC_SERVICE_TO_SERVICE_SECRET_MAP_DEFAULT = "{}";

    // used by dev to by pass the service to service token: Uses user:password format
    static final String RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY = "rbac.service-to-service.exceptional.auth.info";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Secret {
        public String secret;
    }

    private final String authInfo;
    private final String secret;
    private final String application;

    private final Logger log = Logger.getLogger(this.getClass().getName());

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
            log.log(Level.ERROR, "Unable to load Rbac service to service secret map, defaulting to empty map", jsonProcessingException);
            rbacServiceToServiceSecretMap = Map.of();
        }

        secret = rbacServiceToServiceSecretMap.getOrDefault(application, new Secret()).secret;
        if (secret == null) {
            log.log(Level.ERROR, "Unable to load Rbac service to service secret key");
        }

        String tmp = System.getProperty(RBAC_SERVICE_TO_SERVICE_DEV_EXCEPTIONAL_AUTH_KEY);
        if (tmp != null && !tmp.isEmpty()) {
            authInfo = new String(Base64.getEncoder().encode(tmp.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } else {
            authInfo = null;
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (authInfo != null) {
            requestContext.getHeaders().remove("x-rh-rbac-account");
            requestContext.getHeaders().putSingle("Authorization", "Basic " + authInfo);
            return;
        }

        requestContext.getHeaders().putSingle("x-rh-rbac-psk", secret);
        requestContext.getHeaders().putSingle("x-rh-rbac-client-id", application);
    }

    public String getAuthInfo() {
        return authInfo;
    }

    public String getSecret() {
        return secret;
    }

    public String getApplication() {
        return application;
    }
}
