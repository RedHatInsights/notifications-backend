package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void create(Event event, String userIds) {
        entityManager.createNativeQuery("CALL insert_drawer_notifications(:userIds, :orgId, :eventId, :created)")
            .setParameter("userIds", userIds)
            .setParameter("orgId", event.getOrgId())
            .setParameter("eventId", event.getId())
            .setParameter("created", Timestamp.valueOf(event.getCreated())).executeUpdate();
    }
}
