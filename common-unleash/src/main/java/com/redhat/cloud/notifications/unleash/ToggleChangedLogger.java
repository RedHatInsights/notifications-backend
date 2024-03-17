package com.redhat.cloud.notifications.unleash;

import io.getunleash.FeatureToggle;
import io.getunleash.repository.ToggleCollection;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ToggleChangedLogger {

    @ConfigProperty(name = "notifications.unleash.toggle-changed-logger.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    ToggleRegistry toggleRegistry;

    private final Map<String, Boolean> toggleValues = new HashMap<>();

    void process(@Observes ToggleCollection toggleCollection) {
        if (enabled) {
            for (String toggleName : toggleRegistry.getLoggedToggles()) {
                FeatureToggle toggle = toggleCollection.getToggle(toggleName);
                if (toggle != null) {
                    Boolean toggleEnabled = toggleValues.put(toggle.getName(), toggle.isEnabled());
                    if (toggleEnabled == null || toggleEnabled != toggle.isEnabled()) {
                        Log.infof("Feature toggle changed [name=%s, enabled=%s]", toggle.getName(), toggle.isEnabled());
                    }
                } else {
                    toggleValues.remove(toggleName);
                }
            }
        }
    }
}
