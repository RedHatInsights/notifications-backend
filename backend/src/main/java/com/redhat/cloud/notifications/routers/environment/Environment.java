package com.redhat.cloud.notifications.routers.environment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Environment {

    @ConfigProperty(name = "env.name", defaultValue = "local-dev")
    String environment;

    /**
     * Checks if the "ENV_NAME" environment variable is set to "local-dev".
     * @return true if the "ENV_NAME" environment variable is set to "local-dev".
     */
    public boolean isEnvironmentLocal() {
        return "local-dev".equals(this.environment);
    }

    /**
     * Checks if the "ENV_NAME" environment variable is set to "stage".
     * @return true if the "ENV_NAME" environment variable is set to "stage".
     */
    public boolean isEnvironmentStage() {
        return "stage".equals(this.environment);
    }
}
