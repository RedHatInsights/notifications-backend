package com.redhat.cloud.notifications.models;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Environment {

    public static final String LOCAL_ENV = "local-dev";
    public static final String STAGE_ENV = "stage";

    @ConfigProperty(name = "env.name", defaultValue = LOCAL_ENV)
    String environment;

    @ConfigProperty(name = "env.base.url", defaultValue = "/")
    String url;

    public String name() {
        return this.environment;
    }

    public String url() {
        return this.url;
    }

    /**
     * Checks if the "ENV_NAME" environment variable is set to "local-dev".
     * @return true if the "ENV_NAME" environment variable is set to "local-dev".
     */
    public boolean isLocal() {
        return LOCAL_ENV.equals(this.environment);
    }

    /**
     * Checks if the "ENV_NAME" environment variable is set to "stage".
     * @return true if the "ENV_NAME" environment variable is set to "stage".
     */
    public boolean isStage() {
        return STAGE_ENV.equals(this.environment);
    }
}
