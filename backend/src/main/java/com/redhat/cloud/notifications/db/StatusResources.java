package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CurrentStatus;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@ApplicationScoped
public class StatusResources {

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
