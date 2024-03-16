package com.redhat.cloud.notifications.unleash;

import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.repository.ToggleCollection;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.getunleash.repository.FeatureToggleResponse.Status.CHANGED;

@Singleton
@Unremovable
public class Subscriber implements UnleashSubscriber {

    @ConfigProperty(name = "notifications.unleash.enabled", defaultValue = "false")
    boolean unleashEnabled;

    @Inject
    Event<ToggleCollection> toggleCollectionEvent;

    @Override
    public void togglesFetched(FeatureToggleResponse toggleResponse) {
        if (unleashEnabled && toggleResponse.getStatus() == CHANGED) {
            toggleCollectionEvent.fire(toggleResponse.getToggleCollection());
        }
    }
}
