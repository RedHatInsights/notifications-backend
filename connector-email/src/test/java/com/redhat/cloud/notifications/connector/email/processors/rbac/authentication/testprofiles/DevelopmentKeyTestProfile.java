package com.redhat.cloud.notifications.connector.email.processors.rbac.authentication.testprofiles;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class DevelopmentKeyTestProfile implements QuarkusTestProfile {
    public static final String DEVELOPMENT_AUTHENTICATION_KEY = "user:password";

    /**
     * Sets the {@link EmailConnectorConfig#RBAC_DEVELOPMENT_AUTHENTICATION_KEY}
     * to contain the {@link DevelopmentKeyTestProfile#DEVELOPMENT_AUTHENTICATION_KEY}
     * value.
     * @return a configuration override with the configured development key.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(EmailConnectorConfig.RBAC_DEVELOPMENT_AUTHENTICATION_KEY, DEVELOPMENT_AUTHENTICATION_KEY);
    }
}
