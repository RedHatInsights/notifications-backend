package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    public List<Event> getEmailAggregationBasedOnEvent(EventAggregationCriterion key, LocalDateTime start, LocalDateTime end, int firstResultIndex, int maxResults) {
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
}
