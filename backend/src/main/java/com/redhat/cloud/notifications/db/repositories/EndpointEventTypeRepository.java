package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
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
            query += " AND e.id in (:authorizedIds)";
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
    public Endpoint addEventTypeToEndpoint(UUID eventTypeId, UUID endpointId, String orgId) {
        Endpoint endpoint = getEndpoint(endpointId, orgId);

        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
        }

        if (endpoint.getEventTypes() == null) {
            endpoint.setEventTypes(new HashSet<>());
        }
        endpoint.getEventTypes().add(eventType);
        return entityManager.merge(endpoint);
    }

    @Transactional
    public Endpoint updateEventTypesLinkedToEndpoint(UUID endpointId, Set<UUID> eventTypeIds, String orgId) {
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
        return entityManager.merge(endpoint);
    }

    @Transactional
    public void refreshEndpointLinksToEventType(String orgId, List<UUID> endpointsList) {
        if (endpointsList == null || endpointsList.isEmpty()) {
            return;
        }

        String deleteQueryStr = "DELETE FROM EndpointEventType eet WHERE " +
            "endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ") +
            "and id.endpointId in (:endpointList)";

        jakarta.persistence.Query deleteQuery = entityManager.createQuery(deleteQueryStr)
            .setParameter("endpointList", endpointsList);
        if (orgId != null) {
            deleteQuery.setParameter("orgId", orgId);
        }
        deleteQuery.executeUpdate();

        String insertQueryStr = "INSERT INTO EndpointEventType (eventType, endpoint) " +
            "SELECT DISTINCT etb.eventType, bga.endpoint " +
            "from EventTypeBehavior etb inner join BehaviorGroupAction bga on etb.behaviorGroup.id = bga.behaviorGroup.id where " +
            "bga.endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ") +
            "and bga.endpoint.id in (:endpointList)";

        jakarta.persistence.Query insertQuery = entityManager.createQuery(insertQueryStr)
            .setParameter("endpointList", endpointsList);
        if (orgId != null) {
            insertQuery.setParameter("orgId", orgId);
        }
        insertQuery.executeUpdate();
    }

    public void refreshEndpointLinksToEventTypeFromBehaviorGroup(String orgId, Set<UUID> behaviorGroupIds) {
        refreshEndpointLinksToEventType(orgId, findEndpointsByBehaviorGroupId(orgId, behaviorGroupIds));
    }

    public List<UUID> findEndpointsByBehaviorGroupId(String orgId, Set<UUID> behaviorGroupIds) {
        String query = "SELECT bga.endpoint.id FROM BehaviorGroupAction bga WHERE bga.behaviorGroup.id in (:behaviorGroupIds) AND " +
            " bga.endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ");

        TypedQuery<UUID> selectQuery = entityManager.createQuery(query, UUID.class)
            .setParameter("behaviorGroupIds", behaviorGroupIds);
        if (orgId != null) {
            selectQuery.setParameter("orgId", orgId);
        }

        return selectQuery.getResultList();
    }

    private Endpoint getEndpoint(UUID endpointId, String orgId) {
        Endpoint endpoint = endpointRepository.getEndpoint(orgId, endpointId);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }
        return endpoint;
    }

    @Transactional
    public void updateEventTypeEndpoints(String orgId, UUID eventTypeId, Set<UUID> endpointsList) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException(EVENT_TYPE_NOT_FOUND_MESSAGE);
        }

        String deleteQueryStr = "DELETE FROM EndpointEventType eet WHERE " +
            "endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ") +
            "and id.eventTypeId = :eventTypeId";

        jakarta.persistence.Query deleteQuery = entityManager.createQuery(deleteQueryStr)
            .setParameter("eventTypeId", eventTypeId);
        if (orgId != null) {
            deleteQuery.setParameter("orgId", orgId);
        }
        deleteQuery.executeUpdate();

        String insertQueryStr = "INSERT INTO EndpointEventType (eventType, endpoint) " +
            "SELECT DISTINCT evt, ep " +
            "from EventType evt, Endpoint ep where " +
            "ep.orgId " + (orgId == null ? "is null " : "= :orgId ") +
            "and ep.id in (:endpointList) and evt.id = :eventTypeId";

        jakarta.persistence.Query insertQuery = entityManager.createQuery(insertQueryStr)
            .setParameter("endpointList", endpointsList)
            .setParameter("eventTypeId", eventTypeId);
        if (orgId != null) {
            insertQuery.setParameter("orgId", orgId);
        }
        insertQuery.executeUpdate();
    }

    @Transactional
    public void migrateData() {
        String query = "insert into endpoint_event_type (event_type_id, endpoint_id) " +
            "select distinct etb.event_type_id, bga.endpoint_id " +
            "from event_type_behavior etb inner join behavior_group_action bga on etb.behavior_group_id = bga.behavior_group_id " +
            "ON CONFLICT (event_type_id, endpoint_id) DO NOTHING";

        int result = entityManager.createNativeQuery(query).executeUpdate();

        Log.infof("Migrate away from behavior groups: %d records were inserted in 'endpoint_event_type' db table", result);
    }
}
