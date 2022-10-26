package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.CurrentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class StatusRepository {

    @Inject
    EntityManager entityManager;

    public CurrentStatus getCurrentStatus() {
        String query = "FROM CurrentStatus";
        return entityManager.createQuery(query, CurrentStatus.class)
                .getSingleResult();
    }

    @Transactional
    public void setCurrentStatus(CurrentStatus currentStatus) {
        String query = "UPDATE CurrentStatus SET status = :status, startTime = :startTime, endTime = :endTime";
        entityManager.createQuery(query)
                .setParameter("status", currentStatus.getStatus())
                .setParameter("startTime", currentStatus.getStartTime())
                .setParameter("endTime", currentStatus.getEndTime())
                .executeUpdate();
    }
}
