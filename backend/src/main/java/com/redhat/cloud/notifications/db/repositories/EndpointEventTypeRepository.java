package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class EndpointEventTypeRepository {

    private static final String EVENT_TYPE_NOT_FOUND_MESSAGE = "Event type not found";

    @Inject
    EntityManager entityManager;

    @Inject
    EndpointRepository endpointRepository;

    public List<Endpoint> findEndpointsByEventTypeId(String orgId, UUID eventTypeId, Query limiter, Optional<Set<UUID>> authorizedIds) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
        }

        String query = "SELECT e FROM Endpoint e JOIN e.eventTypes ev WHERE (e.orgId = :orgId OR e.orgId IS NULL) AND ev.id = :eventTypeId";

        if (authorizedIds.isPresent()) {
            query += " AND ev.id in (:authorizedIds)";
        }

        if (limiter != null) {
            limiter.setSortFields(Endpoint.SORT_FIELDS);
            limiter.setDefaultSortBy("name:DESC");
            query = limiter.getModifiedQuery(query);
        }

        TypedQuery<Endpoint> typedQuery = entityManager.createQuery(query, Endpoint.class)
            .setParameter("orgId", orgId)
            .setParameter("eventTypeId", eventTypeId);

        if (authorizedIds.isPresent()) {
            typedQuery.setParameter("authorizedIds", authorizedIds.get());
        }

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            typedQuery = typedQuery.setMaxResults(limiter.getLimit().getLimit())
                .setFirstResult(limiter.getLimit().getOffset());
        }

        return typedQuery.getResultList();
    }

    @Transactional
    public void deleteEndpointFromEventType(UUID eventTypeId, UUID endpointId, String orgId) {
        Endpoint endpoint = getEndpoint(endpointId, orgId);

        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
        }

        endpoint.getEventTypes().remove(eventType);

        entityManager.merge(endpoint);
    }

    @Transactional
    public void addEventTypeToEndpoint(UUID eventTypeId, UUID endpointId, String orgId) {
        Endpoint endpoint = getEndpoint(endpointId, orgId);

        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
        }

        if (endpoint.getEventTypes() == null) {
            endpoint.setEventTypes(new HashSet<>());
        }
        endpoint.getEventTypes().add(eventType);
        entityManager.merge(endpoint);
    }

    @Transactional
    public void updateEventTypesLinkedToEndpoint(UUID endpointId, Set<UUID> eventTypeIds, String orgId) {
        Endpoint endpoint = getEndpoint(endpointId, orgId);

        Set<EventType> eventTypes = new HashSet<>();
        for (UUID evID : eventTypeIds) {
            EventType eventType = entityManager.find(EventType.class, evID);
            if (eventType == null) {
                throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
            }
            eventTypes.add(eventType);
        }

        endpoint.setEventTypes(eventTypes);
        entityManager.merge(endpoint);
    }

    private Endpoint getEndpoint(UUID endpointId, String orgId) {
        Endpoint endpoint = endpointRepository.getEndpoint(orgId, endpointId);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }
        return endpoint;
    }
}
