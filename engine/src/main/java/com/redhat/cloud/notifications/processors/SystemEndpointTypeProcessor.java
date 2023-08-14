package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SystemEndpointTypeProcessor extends EndpointTypeProcessor {

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    RecipientResolver recipientResolver;

    @Inject
    FeatureFlipper featureFlipper;

    protected Set<User> getRecipientList(Event event, List<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType) {
        EventType eventType = event.getEventType();

        Set<RecipientSettings> requests = Stream.concat(
            endpoints.stream().map(EndpointRecipientSettings::new),
            ActionRecipientSettings.fromEventWrapper(event.getEventWrapper()).stream()
        ).collect(Collectors.toSet());

        Set<String> subscribers;
        if (featureFlipper.isUseEventTypeForSubscriptionEnabled()) {
            subscribers = Set.copyOf(emailSubscriptionRepository
                    .getSubscribersByEventType(event.getOrgId(), eventType.getId(), emailSubscriptionType));
        } else {
            subscribers = Set.copyOf(emailSubscriptionRepository
                    .getSubscribersByApplication(event.getOrgId(), eventType.getApplicationId(), emailSubscriptionType));
        }

        return recipientResolver.recipientUsers(event.getOrgId(), requests, subscribers, emailSubscriptionType.isOptIn());
    }
}
