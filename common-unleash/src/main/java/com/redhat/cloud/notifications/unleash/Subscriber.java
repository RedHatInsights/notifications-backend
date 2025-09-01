package com.redhat.cloud.notifications.unleash;

import io.getunleash.FeatureDefinition;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.UnleashSubscriber;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

import static io.getunleash.event.ClientFeaturesResponse.Status.CHANGED;

@Singleton
@Unremovable
public class Subscriber implements UnleashSubscriber {

    @ConfigProperty(name = "notifications.unleash.enabled", defaultValue = "false")
    boolean unleashEnabled;

    @Inject
    Event<List<FeatureDefinition>> featureDefinitionsEvent;

    @Override
    public void togglesFetched(ClientFeaturesResponse response) {
        if (unleashEnabled && response.getStatus() == CHANGED) {
            featureDefinitionsEvent.fire(response.getFeatures());
        }
    }
}
