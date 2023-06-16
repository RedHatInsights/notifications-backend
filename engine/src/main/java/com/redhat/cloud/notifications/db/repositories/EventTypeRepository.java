package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeKey;
import com.redhat.cloud.notifications.models.EventTypeKeyBundleAppEventTriplet;
import com.redhat.cloud.notifications.models.EventTypeKeyFqn;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@ApplicationScoped
public class EventTypeRepository {

    @Inject
    EntityManager entityManager;

    public EventType getEventType(EventTypeKey eventTypeKey) {
        if (eventTypeKey instanceof EventTypeKeyBundleAppEventTriplet) {
            EventTypeKeyBundleAppEventTriplet triplet = (EventTypeKeyBundleAppEventTriplet) eventTypeKey;
            return getEventType(triplet.getBundle(), triplet.getApplication(), triplet.getEventType());
        } else if (eventTypeKey instanceof EventTypeKeyFqn) {
            return getEventType(((EventTypeKeyFqn) eventTypeKey).getFullyQualifiedName());
        }

        throw new IllegalArgumentException("Unsupported EventTypeKey found: " + eventTypeKey.getClass());
    }

    public EventType getEventType(String bundleName, String applicationName, String eventTypeName) {
        String query = "FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle b " +
                "WHERE e.name = :eventTypeName AND a.name = :applicationName AND b.name = :bundleName";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResult();
    }

    public EventType getEventType(String fullyQualifiedName) {
        String query = "FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle b " +
                "WHERE e.fullyQualifiedName = :fullyQualifiedName";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("fullyQualifiedName", fullyQualifiedName)
                .getSingleResult();
    }
}
