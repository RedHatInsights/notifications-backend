package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public boolean addEmailAggregation(EmailAggregation aggregation) {
        try {
            entityManager.persist(aggregation);
            return true;
        } catch (Exception e) {
            Log.warn("Email aggregation persisting failed", e);
            return false;
        }
    }

    public List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end, int firstResultIndex, int maxResults) {
        String query = "FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName AND created > :start AND created <= :end ORDER BY created";
        return entityManager.createQuery(query, EmailAggregation.class)
                .setParameter("orgId", key.getOrgId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("start", start)
                .setParameter("end", end)
                .setFirstResult(firstResultIndex)
                .setMaxResults(maxResults)
                .getResultList();
    }

    public List<Event> getEmailAggregationBasedOnEvent(EventAggregationCriteria key, LocalDateTime start, LocalDateTime end, int firstResultIndex, int maxResults) {
        String query = "FROM Event WHERE orgId = :orgId AND applicationId = :applicationId AND created > :start AND created <= :end ORDER BY created";
        return entityManager.createQuery(query, Event.class)
            .setParameter("orgId", key.getOrgId())
            .setParameter("applicationId", key.getApplicationId())
            .setParameter("start", start)
            .setParameter("end", end)
            .setFirstResult(firstResultIndex)
            .setMaxResults(maxResults)
            .getResultList();
    }

    @Transactional
    public int purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return entityManager.createQuery(query)
                .setParameter("orgId", key.getOrgId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
    }
}
