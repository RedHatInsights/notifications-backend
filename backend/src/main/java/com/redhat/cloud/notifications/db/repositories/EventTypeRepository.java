package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class EventTypeRepository {

    @Inject
    EntityManager entityManager;

    public Optional<EventType> find(UUID applicationId, String eventTypeName) {
        String query = "SELECT evt FROM EventType evt WHERE evt.application.id = :applicationId AND evt.name = :eventTypeName";
        try {
            return Optional.of(entityManager.createQuery(query, EventType.class)
                .setParameter("applicationId", applicationId)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<Bundle> findBundleByEventTypeId(UUID eventTypeId) {
        String query = "SELECT evt.application.bundle FROM EventType evt WHERE evt.id = :eventTypeId";
        try {
            return Optional.of(entityManager.createQuery(query, Bundle.class)
                .setParameter("eventTypeId", eventTypeId)
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Map<UUID, Bundle> findBundlesByEventTypeIds(Set<UUID> eventTypeIds) {
        if (eventTypeIds == null || eventTypeIds.isEmpty()) {
            return Map.of();
        }

        String query = "SELECT evt FROM EventType evt JOIN FETCH evt.application app JOIN FETCH app.bundle WHERE evt.id IN (:eventTypeIds)";
        List<EventType> eventTypes = entityManager.createQuery(query, EventType.class)
            .setParameter("eventTypeIds", eventTypeIds)
            .getResultList();

        Map<UUID, Bundle> result = new HashMap<>();
        for (EventType eventType : eventTypes) {
            result.put(eventType.getId(), eventType.getApplication().getBundle());
        }
        return result;
    }

    /**
     * Finds the event types by the given event type identifiers.
     * @param eventTypeIds the identifiers to find the event types with.
     * @return a map containing the event type's {@link UUID} as the key, and
     * the event type itself as the value, so that lookups by identifier are
     * fast. */
    public List<EventType> findByIds(final Set<UUID> eventTypeIds) {
        return entityManager
            .createQuery("FROM EventType WHERE id IN (:eventTypeIds)", EventType.class)
            .setParameter("eventTypeIds", eventTypeIds)
            .getResultList();
    }

    /**
     * Get all event type IDs where includedInDrawer = true.
     * Used for drawer subscribe-by-default logic.
     *
     * @return Set of event type UUIDs that are included in drawer
     */
    public Set<UUID> getEventTypeIdsByIncludedInDrawer() {
        String query = "SELECT id FROM EventType WHERE includedInDrawer = true";
        List<UUID> results = entityManager.createQuery(query, UUID.class)
            .getResultList();
        return Set.copyOf(results);
    }
}
