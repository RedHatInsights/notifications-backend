package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Event create(Event event) {
        entityManager.persist(event);
        return event;
    }

    @Transactional
    public void updateEventDisplayName(UUID eventId, String eventTypeDisplayName) {
        String hql = "UPDATE Event SET eventTypeDisplayName = :eventDisplayName WHERE id = :id";
        entityManager.createQuery(hql)
            .setParameter("eventDisplayName", eventTypeDisplayName)
            .setParameter("id", eventId)
            .executeUpdate();
    }
}
