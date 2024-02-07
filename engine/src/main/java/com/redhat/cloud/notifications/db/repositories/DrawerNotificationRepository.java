package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.util.List;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<DrawerNotification> create(Event event, String users) {
        return entityManager.createNativeQuery("SELECT * FROM insert_drawer_notifications(:users, :orgId, :eventId, :created)", DrawerNotification.class)
            .setParameter("users", users)
            .setParameter("orgId", event.getOrgId())
            .setParameter("eventId", event.getId())
            .setParameter("created", Timestamp.valueOf(event.getCreated())).getResultList();
    }

    @Transactional
    public void createWithId(Event event, String drawerNotificationIdList) {
        entityManager.createNativeQuery("CALL insert_drawer_notifications_with_id(:drawerNotificationIdList, :orgId, :eventId, :created)")
            .setParameter("drawerNotificationIdList", drawerNotificationIdList)
            .setParameter("orgId", event.getOrgId())
            .setParameter("eventId", event.getId())
            .setParameter("created", Timestamp.valueOf(event.getCreated())).executeUpdate();
    }
}
