package com.redhat.cloud.notifications.unleash;

import io.getunleash.FeatureDefinition;
import io.getunleash.event.ClientFeaturesResponse;
import io.getunleash.event.UnleashSubscriber;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

import static io.getunleash.event.ClientFeaturesResponse.Status.CHANGED;

@Singleton
@Unremovable
public class Subscriber implements UnleashSubscriber {

    @Inject
    Event<List<FeatureDefinition>> featureDefinitionsEvent;

    @Override
    public void togglesFetched(ClientFeaturesResponse response) {
        if (response.getStatus() == CHANGED) {
            featureDefinitionsEvent.fire(response.getFeatures());
        }
    }
}
