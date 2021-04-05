package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ApplicationResources {

    @Inject
    Mutiny.Session session;

    public Uni<Application> createApplication(Application app) {
        // The returned app will contain an ID and a creation timestamp.
        return Uni.createFrom().item(app)
                .onItem().transform(this::addBundleReference)
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(app);
    }

    public Uni<Boolean> deleteApplication(UUID id) {
        String query = "DELETE FROM Application WHERE id = :id";
        return session.createQuery(query)
                .setParameter("id", id)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Uni<EventType> addEventTypeToApplication(UUID appId, EventType eventType) {
        return Uni.createFrom().item(eventType)
                .onItem().transform(et -> {
                    et.setApplication(session.getReference(Application.class, appId));
                    return et;
                })
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(eventType)
                .onItem().transform(EventType::filterOutApplication);
    }

    public Multi<Application> getApplications(String bundleName) {
        String query = "FROM Application WHERE bundle.name = :bundleName";
        return session.createQuery(query, Application.class)
                .setParameter("bundleName", bundleName)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<Application> getApplication(UUID id) {
        return session.find(Application.class, id);
    }

    public Uni<Application> getApplication(String bundleName, String applicationName) {
        String query = "FROM Application WHERE bundle.name = :bundleName AND name = :applicationName";
        return session.createQuery(query, Application.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .getSingleResultOrNull();
    }

    public Uni<EventType> getEventType(String bundleName, String applicationName, String eventTypeName) {
        final String query = "FROM EventType WHERE name = :eventTypeName AND application.name = :applicationName AND application.bundle.name = :bundleName";
        return session.createQuery(query, EventType.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResultOrNull();
    }

    public Multi<EventType> getEventTypes(UUID appId) {
        String query = "FROM EventType WHERE application.id = :appId";
        return session.createQuery(query, EventType.class)
                .setParameter("appId", appId)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(EventType::filterOutApplication);
    }

    public Uni<Boolean> deleteEventTypeById(UUID id) {
        String query = "DELETE FROM EventType WHERE id = :id";
        return session.createQuery(query)
                .setParameter("id", id)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Multi<EventType> getEventTypes(Query limiter, Set<UUID> appIds, UUID bundleId) {
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

        return mutinyQuery.getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<List<EventType>> getEventTypesByEndpointId(String accountId, UUID endpointId) {
        String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b JOIN b.behaviorGroup.actions a " +
                "WHERE b.behaviorGroup.accountId = :accountId AND a.endpoint.id = :endpointId";
        return session.createQuery(query, EventType.class)
                .setParameter("accountId", accountId)
                .setParameter("endpointId", endpointId)
                .getResultList();
    }

    /**
     * Adds to the given {@link Application} a reference to a persistent {@link Bundle} without actually loading its
     * state from the database. The app will remain unchanged if it does not contain a non-null bundle identifier.
     *
     * @param app the app that will hold the bundle reference
     * @return the same app instance, possibly modified if a bundle reference was added
     */
    private Application addBundleReference(Application app) {
        if (app.getBundleId() != null && app.getBundle() == null) {
            app.setBundle(session.getReference(Bundle.class, app.getBundleId()));
        }
        return app;
    }
}
