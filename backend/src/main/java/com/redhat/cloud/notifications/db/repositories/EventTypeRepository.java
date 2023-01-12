package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
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
}
