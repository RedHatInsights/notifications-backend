package com.redhat.cloud.notifications.unleash.utils;

import io.getunleash.FeatureDefinition;
import io.getunleash.Unleash;
import io.getunleash.variant.Payload;
import io.getunleash.variant.Variant;
import io.quarkus.logging.Log;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@Singleton
public class PodRestartRequestedChecker {

    private static final String UNLEASH_TOGGLE_NAME = "notifications.pod-restart-requested";

    @ConfigProperty(name = "host-name", defaultValue = "localhost")
    String hostName;

    @Inject
    Unleash unleash;

    boolean restartRequestedFromUnleash = false;

    public void process(@Observes List<FeatureDefinition> featureDefinitions) {
        Variant variant = unleash.getVariant(UNLEASH_TOGGLE_NAME);
        if (variant.isEnabled()) {
            Optional<Payload> payload = variant.getPayload();
            if (payload.isEmpty() || payload.get().getValue() == null) {
                Log.warn("Variant ignored because of an empty payload");
                return;
            }

            restartRequestedFromUnleash = hostName.equals(payload.get().getValue());
        }
    }

    public boolean isRestartRequestedFromUnleash() {
        return restartRequestedFromUnleash;
    }
}
