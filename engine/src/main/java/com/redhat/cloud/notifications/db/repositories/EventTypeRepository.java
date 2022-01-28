package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventTypeRepository {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<EventType> getEventType(String bundleName, String applicationName, String eventTypeName) {
        final String query = "FROM EventType WHERE name = :eventTypeName AND application.name = :applicationName AND application.bundle.name = :bundleName";
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query, EventType.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("eventTypeName", eventTypeName)
                    .getSingleResult();
        });
    }
}
