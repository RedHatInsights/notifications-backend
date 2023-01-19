package com.redhat.cloud.notifications.templates.models;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Environment {

    @ConfigProperty(name = "env.name", defaultValue = "local-dev")
    String environment;

    @ConfigProperty(name = "env.base.url", defaultValue = "/")
    String url;

    public String name() {
        return this.environment;
    }

    public String url() {
        return this.url;
    }
}
