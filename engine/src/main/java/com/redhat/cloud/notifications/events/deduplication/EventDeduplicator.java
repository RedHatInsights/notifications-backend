package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.ValkeyService;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventDeduplicator {

    private static final DefaultEventDeduplicationConfig DEFAULT_DEDUPLICATION_CONFIG = new DefaultEventDeduplicationConfig();
    private static final String SUBSCRIPTION_SERVICES_BUNDLE = "subscription-services";
    private static final String SUBSCRIPTIONS_APP = "subscriptions";

    @Inject
    EntityManager entityManager;

    @Inject
    SubscriptionsDeduplicationConfig subscriptionsDeduplicationConfig;

    @Inject
    EngineConfig engineConfig;

    @Inject
    ValkeyService valkeyService;

    public EventDeduplicationConfig getEventDeduplicationConfig(Event event) {
        return switch (event.getEventType().getApplication().getBundle().getName()) {
            case SUBSCRIPTION_SERVICES_BUNDLE ->
                switch (event.getEventType().getApplication().getName()) {
                    case SUBSCRIPTIONS_APP -> subscriptionsDeduplicationConfig;
                    default -> DEFAULT_DEDUPLICATION_CONFIG;
                };
            default -> DEFAULT_DEDUPLICATION_CONFIG;
        };
    }

    @Transactional
    public boolean isNew(Event event) {

        EventDeduplicationConfig eventDeduplicationConfig = getEventDeduplicationConfig(event);
        Optional<String> deduplicationKey = eventDeduplicationConfig.getDeduplicationKey(event);

        // Events are always considered new if no deduplication key is available.
        if (deduplicationKey.isEmpty()) {
            return true;
        }

        UUID eventTypeId = event.getEventType().getId();
        LocalDateTime deleteAfter = eventDeduplicationConfig.getDeleteAfter(event);

        if (engineConfig.isInMemoryDbEnabled() && engineConfig.isValkeyEventDeduplicatorEnabled()) {
            // RHCLOUD-35790: remove once Valkey deduplication is validated
            boolean isNewEvent = postgresEventDeduplication(eventTypeId, deduplicationKey, deleteAfter);
            boolean valkeyIsNewEvent = valkeyService.isNewEvent(eventTypeId, deduplicationKey.get(),
                    deleteAfter);
            if (valkeyIsNewEvent != isNewEvent) {
                Log.warnf(
                        "Valkey event deduplication (isNewEvent=%s) does not align with Postgres result (isNewEvent=%s) [event_type_id=%s, deduplication_key=%s]",
                        valkeyIsNewEvent, isNewEvent, eventTypeId, deduplicationKey.get());
            }

            return isNewEvent;

        } else {
            return postgresEventDeduplication(eventTypeId, deduplicationKey, deleteAfter);
        }
    }

    private boolean postgresEventDeduplication(UUID eventTypeId, Optional<String> deduplicationKey, LocalDateTime deleteAfter) {
        String sql = "INSERT INTO event_deduplication(event_type_id, deduplication_key, delete_after) " +
                "VALUES (:eventTypeId, :deduplicationKey, :deleteAfter) " +
                "ON CONFLICT (event_type_id, deduplication_key) DO NOTHING";

        int rowCount = entityManager.createNativeQuery(sql)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("deduplicationKey", deduplicationKey.get())
                .setParameter("deleteAfter", deleteAfter)
                .executeUpdate();
        return rowCount > 0;
    }
}
