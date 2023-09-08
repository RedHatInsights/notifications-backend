package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<DrawerNotification> create(Event event, String users) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("insert_drawer_notifications", DrawerNotification.class)
            .registerStoredProcedureParameter(1, String.class, ParameterMode.IN)
            .registerStoredProcedureParameter(2, String.class, ParameterMode.IN)
            .registerStoredProcedureParameter(3, UUID.class, ParameterMode.IN)
            .registerStoredProcedureParameter(4, Timestamp.class, ParameterMode.IN)

            .setParameter(1, users)
            .setParameter(2, event.getOrgId())
            .setParameter(3, event.getId())
            .setParameter(4, Timestamp.valueOf(event.getCreated()));

        return query.getResultList();
    }
}
