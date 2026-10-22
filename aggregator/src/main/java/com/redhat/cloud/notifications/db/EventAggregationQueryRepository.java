package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EventAggregationQueryRepository {

    @Inject
    EntityManager entityManager;

    public List<Event> getEventsForAggregation(EventAggregationCriterion key, LocalDateTime start, LocalDateTime end, int firstResultIndex, int maxResults) {
        String query = "FROM Event JOIN FETCH eventType WHERE orgId = :orgId AND applicationId = :applicationId AND created > :start AND created <= :end ORDER BY created";
        return entityManager.createQuery(query, Event.class)
            .setParameter("orgId", key.getOrgId())
            .setParameter("applicationId", key.getApplicationId())
            .setParameter("start", start)
            .setParameter("end", end)
            .setFirstResult(firstResultIndex)
            .setMaxResults(maxResults)
            .getResultList();
    }

    /**
     * Finds any single already-persisted event matching the given criterion, used to anchor an
     * offloaded {@code payload_details} row's {@code event_id} FK when a pre-folded digest payload is
     * too large to send inline. Which event is returned doesn't matter -- only that it's a real,
     * already-persisted row.
     */
    public Optional<Event> findAnyEvent(EventAggregationCriterion key, LocalDateTime start, LocalDateTime end) {
        List<Event> events = getEventsForAggregation(key, start, end, 0, 1);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }
}
