package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.util.Optional;
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
}
