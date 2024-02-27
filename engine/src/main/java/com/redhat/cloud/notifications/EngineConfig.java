package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import io.getunleash.Unleash;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EngineConfig {

    @Inject
    Unleash unleash;

    @Inject
    FeatureFlipper featureFlipper;

    public boolean isEmailsOnlyMode() {
        return unleash.isEnabled("notifications-engine.emails-only-mode.enabled", featureFlipper.isEmailsOnlyMode());
    }
}
