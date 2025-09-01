package com.redhat.cloud.notifications.unleash;

import io.getunleash.FeatureDefinition;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ToggleChangedLogger {

    @ConfigProperty(name = "notifications.unleash.toggle-changed-logger.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    ToggleRegistry toggleRegistry;

    private final Map<String, Boolean> toggleValues = new HashMap<>();

    void process(@Observes List<FeatureDefinition> featureDefinitions) {
        if (enabled) {
            for (String toggleName : toggleRegistry.getLoggedToggles()) {
                Optional<FeatureDefinition> toggle = featureDefinitions.stream()
                    .filter(featureDefinition -> toggleName.equals(featureDefinition.getName()))
                    .findFirst();
                if (toggle.isPresent()) {
                    Boolean toggleEnabled = toggleValues.put(toggle.get().getName(), toggle.get().environmentEnabled());
                    if (toggleEnabled == null || toggleEnabled != toggle.get().environmentEnabled()) {
                        Log.infof("Feature toggle changed [name=%s, enabled=%s]", toggle.get().getName(), toggle.get().environmentEnabled());
                    }
                } else {
                    toggleValues.remove(toggleName);
                }
            }
        }
    }
}
