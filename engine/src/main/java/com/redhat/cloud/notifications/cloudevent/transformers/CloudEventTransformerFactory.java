package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;

import javax.enterprise.context.ApplicationScoped;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class CloudEventTransformerFactory {

    private static final String POLICIES_POLICY_TRIGGERED_TYPE = "com.redhat.console.policies.policy-triggered";

    /**
     * Returns a CloudEventTransformer if the transforming is supported
     */
    public Optional<CloudEventTransformer> getTransformerIfSupported(EventWrapperCloudEvent cloudEvent) {
        switch (cloudEvent.getEvent().get("type").asText().toLowerCase(Locale.ROOT)) {
            case POLICIES_POLICY_TRIGGERED_TYPE:
                return Optional.of(new PolicyTriggeredCloudEventTransformer());
            default:
                return Optional.empty();
        }
    }
}
