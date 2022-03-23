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
        return url("/");
    }

    public String url(String path) {
        final String base;

        switch (environment) {
            case "prod":
                base = "https://console.redhat.com";
                break;
            case "stage":
                base = "https://console.stage.redhat.com";
                break;
            default:
                base = "";
                break;
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return base + path;
    }
}
