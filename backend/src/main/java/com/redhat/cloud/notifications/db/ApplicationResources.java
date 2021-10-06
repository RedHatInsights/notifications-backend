package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ApplicationResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Application> createApp(Application app) {
        return sessionFactory.withSession(session -> {
            return session.find(Bundle.class, app.getBundleId())
                    .onItem().ifNull().failWith(new NotFoundException())
                    .onItem().transform(bundle -> {
                        app.setBundle(bundle);
                        return app;
                    })
                    .onItem().transformToUni(session::persist)
                    .call(session::flush)
                    .replaceWith(app);
        });
    }

    public Uni<Integer> updateApplication(UUID id, Application app) {
        String query = "UPDATE Application SET name = :name, displayName = :displayName WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("name", app.getName())
                    .setParameter("displayName", app.getDisplayName())
                    .setParameter("id", id)
                    .executeUpdate()
                    .call(session::flush);
        });
    }

    public Uni<Boolean> deleteApplication(UUID id) {
        String query = "DELETE FROM Application WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("id", id)
                    .executeUpdate()
                    .call(session::flush)
                    .onItem().transform(rowCount -> rowCount > 0);
        });
    }

    public Uni<EventType> createEventType(EventType eventType) {
        return sessionFactory.withSession(session -> {
            return session.find(Application.class, eventType.getApplicationId())
                    .onItem().ifNull().failWith(new NotFoundException())
                    .onItem().transform(app -> {
                        eventType.setApplication(app);
                        return eventType;
                    })
                    .onItem().transformToUni(session::persist)
                    .call(session::flush)
                    .replaceWith(eventType)
                    .onItem().transform(EventType::filterOutApplication);
        });
    }

    public Uni<List<Application>> getApplications(String bundleName) {
        return sessionFactory.withSession(session -> {
            String sql = "FROM Application";
            if (bundleName != null) {
                sql += " WHERE bundle.name = :bundleName";
            }

            var query = session.createQuery(sql, Application.class);

            if (bundleName != null) {
                query = query.setParameter("bundleName", bundleName);
            }

            return query.getResultList();
        });
    }

    public Uni<Application> getApplication(UUID id) {
        return sessionFactory.withSession(session -> {
            return session.find(Application.class, id);
        });
    }

    public Uni<Application> getApplication(String bundleName, String applicationName) {
        String query = "FROM Application WHERE bundle.name = :bundleName AND name = :applicationName";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, Application.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .getSingleResultOrNull();
        });
    }

    // Note: This method uses a stateless session
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

    public Uni<List<EventType>> getEventTypes(UUID appId) {
        String query = "FROM EventType WHERE application.id = :appId";
        return sessionFactory.withSession(session -> {
            return session.find(Application.class, appId)
                    .onItem().ifNull().failWith(new NotFoundException())
                    .replaceWith(
                            session.createQuery(query, EventType.class)
                                    .setParameter("appId", appId)
                                    .getResultList()
                    )
                    .onItem().invoke(eventTypes -> {
                        for (EventType eventType : eventTypes) {
                            eventType.filterOutApplication();
                        }
                    });
        });
    }

    public Uni<Integer> updateEventType(UUID id, EventType eventType) {
        String query = "UPDATE EventType SET name = :name, displayName = :displayName, description = :description WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("name", eventType.getName())
                    .setParameter("displayName", eventType.getDisplayName())
                    .setParameter("description", eventType.getDescription())
                    .setParameter("id", id)
                    .executeUpdate()
                    .call(session::flush);
        });
    }

    public Uni<Boolean> deleteEventTypeById(UUID id) {
        String query = "DELETE FROM EventType WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("id", id)
                    .executeUpdate()
                    .call(session::flush)
                    .onItem().transform(rowCount -> rowCount > 0);
        });
    }

    public Uni<List<EventType>> getEventTypes(Query limiter, Set<UUID> appIds, UUID bundleId) {
        return sessionFactory.withSession(session -> {
            String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application";

            List<String> conditions = new ArrayList<>();
            if (appIds != null && appIds.size() > 0) {
                conditions.add("e.application.id IN (:appIds)");
            }
            if (bundleId != null) {
                conditions.add("e.application.bundle.id = :bundleId");
            }
            if (conditions.size() > 0) {
                query += " WHERE " + String.join(" AND ", conditions);
            }

            if (limiter != null) {
                query = limiter.getModifiedQuery(query);
            }

            Mutiny.Query<EventType> mutinyQuery = session.createQuery(query, EventType.class);
            if (appIds != null && appIds.size() > 0) {
                mutinyQuery = mutinyQuery.setParameter("appIds", appIds);
            }
            if (bundleId != null) {
                mutinyQuery = mutinyQuery.setParameter("bundleId", bundleId);
            }

            if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
                mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                        .setFirstResult(limiter.getLimit().getOffset());
            }

            return mutinyQuery.getResultList();
        });
    }
}
