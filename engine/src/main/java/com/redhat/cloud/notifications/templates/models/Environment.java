package com.redhat.cloud.notifications.templates.models;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Environment {

    @ConfigProperty(name = "env.name", defaultValue = "local-dev")
    String environment;

    public String name() {
        return this.environment;
    }

    public String url() {

        switch (environment) {
            case "prod":
                return "https://console.redhat.com";
            case "stage":
                return "https://console.stage.redhat.com";
            default:
                return "/";
        }
    }
}
