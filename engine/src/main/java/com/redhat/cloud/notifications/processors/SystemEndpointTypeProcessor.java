package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class SystemEndpointTypeProcessor extends EndpointTypeProcessor {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    RecipientResolver recipientResolver;

    protected Set<User> getRecipientList(Event event, List<Endpoint> endpoints, SubscriptionType subscriptionType) {
        EventType eventType = event.getEventType();

        final Set<RecipientSettings> requests = extractRecipientSettings(event, endpoints);

        Set<String> subscribers;
        if (subscriptionType.isSubscribedByDefault()) {
            subscribers = Set.copyOf(subscriptionRepository
                    .getUnsubscribers(event.getOrgId(), eventType.getId(), subscriptionType));
        } else {
            subscribers = Set.copyOf(subscriptionRepository
                    .getSubscribers(event.getOrgId(), eventType.getId(), subscriptionType));
        }
        // This is supposed to be removed soon. As a consequence, this call will not support the event types subscribed by default.
        return recipientResolver.recipientUsers(event.getOrgId(), requests, subscribers, !subscriptionType.isSubscribedByDefault());
    }

    /**
     * Extracts the recipient settings.
     * @param event the event to extract the recipient settings from.
     * @param endpoints the list of endpoints to extract the recipient settings
     *                  from.
     * @return a set of recipient settings which combines the ones extracted
     * from the event and the ones extracted from the endpoints.
     */
    protected Set<RecipientSettings> extractRecipientSettings(final Event event, final List<Endpoint> endpoints) {
        return Stream.concat(
            endpoints.stream().map(EndpointRecipientSettings::new),
            ActionRecipientSettings.fromEventWrapper(event.getEventWrapper()).stream()
        ).collect(Collectors.toSet());
    }

    /**
     * Extracts the recipient settings from the event and the endpoints and
     * transforms them to a DTO in order to send them to the email connector.
     * @param event the event to extract the recipient settings from.
     * @param endpoints the endpoints to extract the recipient settings from.
     * @return a set of recipient settings DTOs.
     */
    public Set<com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings> extractAndTransformRecipientSettings(final Event event, final List<Endpoint> endpoints) {
        final Set<RecipientSettings> recipientSettings = this.extractRecipientSettings(event, endpoints);

        return recipientSettings
            .stream()
            .map(com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings::new)
            .collect(Collectors.toSet());
    }

    /**
     * Gets a list of the email subscribers which should get an email for the
     * given event.
     * @param event the event the users are subscribed to.
     * @return a list of {@link java.util.UUID}s in the {@link String} shape.
     */
    protected List<String> getSubscribers(final Event event, final SubscriptionType subscriptionType) {
        final EventType eventType = event.getEventType();

        if (subscriptionType.isSubscribedByDefault()) {
            return subscriptionRepository.getUnsubscribers(event.getOrgId(), eventType.getId(), subscriptionType);
        } else {
            return subscriptionRepository.getSubscribers(event.getOrgId(), eventType.getId(), subscriptionType);
        }
    }
}
