package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class CloudEventTransformerFactory {

    private static final String POLICIES_POLICY_TRIGGERED_TYPE = "com.redhat.console.insights.policies.policy-triggered";
    @Inject
    PolicyTriggeredCloudEventTransformer policyTriggeredCloudEventTransformer;

    /**
     * Returns a CloudEventTransformer if the transforming is supported
     */
    public Optional<CloudEventTransformer> getTransformerIfSupported(EventWrapperCloudEvent cloudEvent) {
        switch (cloudEvent.getEvent().getType().toLowerCase(Locale.ROOT)) {
            case POLICIES_POLICY_TRIGGERED_TYPE:
                return Optional.of(policyTriggeredCloudEventTransformer);
            default:
                return Optional.empty();
        }
    }
}
