package com.redhat.cloud.notifications.connector.email.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Environment {
    public static final String DEVELOPMENT = "development";

    @ConfigProperty(name = "env.name", defaultValue = DEVELOPMENT)
    String environment;

    /**
     * Checks if the "ENV_NAME" environment variable is set to "development".
     * @return true if the "ENV_NAME" environment variable is set to "development".
     */
    public boolean isDevelopmentEnvironment() {
        return DEVELOPMENT.equals(this.environment);
    }
}
