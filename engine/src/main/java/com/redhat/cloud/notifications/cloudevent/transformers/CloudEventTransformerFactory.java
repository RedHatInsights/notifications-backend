package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventDataCloudEvent;

import java.util.Locale;
import java.util.Optional;

public class CloudEventTransformerFactory {

    private static final String POLICIES_POLICY_TRIGGERED_TYPE = "com.redhat.console.policies.policy-triggered";

    /**
     * Returns a CloudEventTransformer if the transforming is supported
     */
    public static Optional<CloudEventTransformer> getTransformerIfSupported(EventDataCloudEvent cloudEvent) {
        switch (cloudEvent.getRawEvent().get("type").asText().toLowerCase(Locale.ROOT)) {
            case POLICIES_POLICY_TRIGGERED_TYPE:
                return Optional.of(new PoliciesCloudEventTransformer());
            default:
                return Optional.empty();
        }
    }
}
