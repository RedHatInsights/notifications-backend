package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import io.getunleash.Unleash;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EngineConfig {

    @ConfigProperty(name = "notifications.unleash.enabled", defaultValue = "false")
    boolean unleashEnabled;

    @Inject
    Unleash unleash;

    @Inject
    FeatureFlipper featureFlipper;

    public boolean isEmailsOnlyMode() {
        if (unleashEnabled) {
            return unleash.isEnabled("notifications-engine.emails-only-mode.enabled", false);
        } else {
            return featureFlipper.isEmailsOnlyMode();
        }
    }
}
