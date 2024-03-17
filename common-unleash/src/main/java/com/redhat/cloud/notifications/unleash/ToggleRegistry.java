package com.redhat.cloud.notifications.unleash;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ApplicationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class ToggleRegistry {

    private static final String DEFAULT_APP_NAME = "notifications-default-app-name";

    @Inject
    ApplicationConfig applicationConfig;

    private final Set<String> loggedToggles = new HashSet<>();

    public String register(String feature, boolean logChanges) {

        if (applicationConfig == null) {
            throw new IllegalStateException("This method must be called after CDI is done initializing");
        }

        String appName = applicationConfig.name.orElseGet(() -> {
            // This should only happen when tests are executed.
            Log.warnf("Application name not found in the Quarkus config, defaulting to %s", DEFAULT_APP_NAME);
            return DEFAULT_APP_NAME;
        });

        String toggleName = String.format("%s.%s.enabled", appName, feature);

        if (logChanges) {
            loggedToggles.add(toggleName);
        }

        return toggleName;
    }

    public Set<String> getLoggedToggles() {
        return loggedToggles;
    }
}
