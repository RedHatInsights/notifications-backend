package com.redhat.cloud.notifications.processors;

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

    protected Set<User> getRecipientList(Event event, List<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType) {
        EventType eventType = event.getEventType();
        String bundleName = eventType.getApplication().getBundle().getName();
        String applicationName = eventType.getApplication().getName();
        String eventTypeName = eventType.getName();

        Set<RecipientSettings> requests = Stream.concat(
            endpoints.stream().map(EndpointRecipientSettings::new),
            ActionRecipientSettings.fromEventWrapper(event.getEventWrapper()).stream()
        ).collect(Collectors.toSet());

        Set<String> subscribers = Set.copyOf(emailSubscriptionRepository
            .getEmailSubscribersUserId(event.getOrgId(), bundleName, applicationName, eventTypeName, emailSubscriptionType));

        return recipientResolver.recipientUsers(event.getOrgId(), requests, subscribers, emailSubscriptionType.isOptIn());
    }
}
