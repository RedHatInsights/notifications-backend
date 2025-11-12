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
        switch (event.getEventType().getApplication().getBundle().getName()) {
            case SUBSCRIPTION_SERVICES_BUNDLE -> {
                switch (event.getEventType().getApplication().getName()) {
                    case SUBSCRIPTIONS_APP -> new SubscriptionsDeduplicationConfig(event);
                    default -> {
                        // Do nothing. The default event deduplication config will be used.
                    }
                }
            }
            default -> {
                // Do nothing. The default event deduplication config will be used.
            }
        }
        return new DefaultEventDeduplicationConfig(event);
    }

    @Transactional
    public boolean isNew(Event event) {

        EventDeduplicationConfig eventDeduplicationConfig = getEventDeduplicationConfig(event);

        String sql = "INSERT INTO event_deduplication(event_type_id, deduplication_key, delete_after) " +
                "VALUES (:eventTypeId, :deduplicationKey, :deleteAfter) " +
                "ON CONFLICT DO NOTHING";

        int rowCount = entityManager.createNativeQuery(sql)
                .setParameter("eventTypeId", event.getEventType().getId())
                .setParameter("deduplicationKey", eventDeduplicationConfig.getDeduplicationKey())
                .setParameter("deleteAfter", eventDeduplicationConfig.getDeleteAfter())
                .executeUpdate();
        return rowCount > 0;
    }
}
