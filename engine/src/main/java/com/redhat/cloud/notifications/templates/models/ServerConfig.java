package com.redhat.cloud.notifications.templates.models;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ServerConfig {

    @ConfigProperty(name = "env", defaultValue = "local-dev")
    String environment;

    public String environment() {
        return this.environment;
    }

    public String url(String path) {
        String base = "";

        switch (environment) {
            case "prod":
                base = "https://console.redhat.com";
                break;
            case "stage":
                base = "https://console.stage.redhat.com";
                break;
        }

        return base + path;
    }
}
