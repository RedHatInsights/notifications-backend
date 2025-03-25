package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeKey;
import com.redhat.cloud.notifications.models.EventTypeKeyBundleAppEventTriplet;
import com.redhat.cloud.notifications.models.EventTypeKeyFqn;
import com.redhat.cloud.notifications.models.dto.BundleApplicationEventTypeDTO;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;

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

    @CacheResult(cacheName = "event-types-from-baet")
    public EventType getEventType(String bundleName, String applicationName, String eventTypeName) {
        String query = "FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle b " +
                "WHERE e.name = :eventTypeName AND a.name = :applicationName AND b.name = :bundleName";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResult();
    }

    @CacheResult(cacheName = "event-types-from-fqn")
    public EventType getEventType(String fullyQualifiedName) {
        String query = "FROM EventType e JOIN FETCH e.application a JOIN FETCH a.bundle b " +
                "WHERE e.fullyQualifiedName = :fullyQualifiedName";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("fullyQualifiedName", fullyQualifiedName)
                .getSingleResult();
    }

    public BundleApplicationEventTypeDTO getEventTypeBaet(UUID id) {
        String query = "SELECT new com.redhat.cloud.notifications.models.dto.BundleApplicationEventTypeDTO(b.name, a.name, e.name) " +
            "FROM EventType e, Application a, Bundle b " +
            "WHERE e.id = :eventTypeId and e.application.id = a.id and a.bundle.id = b.id";
        return entityManager.createQuery(query, BundleApplicationEventTypeDTO.class)
            .setParameter("eventTypeId", id)
            .getSingleResult();
    }
}
