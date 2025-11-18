package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class EventDeduplicator {

    private static final String SUBSCRIPTION_SERVICES_BUNDLE = "subscription-services";
    private static final String SUBSCRIPTIONS_APP = "subscriptions";

    @Inject
    EntityManager entityManager;

    private EventDeduplicationConfig getEventDeduplicationConfig(Event event) {
        return switch (event.getEventType().getApplication().getBundle().getName()) {
            case SUBSCRIPTION_SERVICES_BUNDLE ->
                switch (event.getEventType().getApplication().getName()) {
                    case SUBSCRIPTIONS_APP -> new SubscriptionsDeduplicationConfig(event);
                    default -> new DefaultEventDeduplicationConfig(event);
                };
            default -> new DefaultEventDeduplicationConfig(event);
        };
    }

    @Transactional
    public boolean isNew(Event event) {

        EventDeduplicationConfig eventDeduplicationConfig = getEventDeduplicationConfig(event);

        // Events are always considered new if no deduplication key is available.
        if (eventDeduplicationConfig.getDeduplicationKey().isEmpty()) {
            return true;
        }

        String sql = "INSERT INTO event_deduplication(event_type_id, deduplication_key, delete_after) " +
                "VALUES (:eventTypeId, :deduplicationKey, :deleteAfter) " +
                "ON CONFLICT (event_type_id, deduplication_key) DO NOTHING";

        int rowCount = entityManager.createNativeQuery(sql)
                .setParameter("eventTypeId", event.getEventType().getId())
                .setParameter("deduplicationKey", eventDeduplicationConfig.getDeduplicationKey().get())
                .setParameter("deleteAfter", eventDeduplicationConfig.getDeleteAfter())
                .executeUpdate();
        return rowCount > 0;
    }
}
