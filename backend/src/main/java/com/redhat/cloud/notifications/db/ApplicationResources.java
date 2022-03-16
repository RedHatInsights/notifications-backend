package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.builder.JoinBuilder;
import com.redhat.cloud.notifications.db.builder.QueryBuilder;
import com.redhat.cloud.notifications.db.builder.WhereBuilder;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ApplicationResources {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Application createApp(Application app) {
        Bundle bundle = entityManager.find(Bundle.class, app.getBundleId());
        if (bundle == null) {
            throw new NotFoundException();
        } else {
            app.setBundle(bundle);
            entityManager.persist(app);
            return app;
        }
    }

    @Transactional
    public int updateApplication(UUID id, Application app) {
        String appQuery = "UPDATE Application SET name = :name, displayName = :displayName WHERE id = :id";
        int rowCount = entityManager.createQuery(appQuery)
                .setParameter("name", app.getName())
                .setParameter("displayName", app.getDisplayName())
                .setParameter("id", id)
                .executeUpdate();
        String eventQuery = "UPDATE Event SET applicationDisplayName = :displayName WHERE applicationId = :applicationId";
        entityManager.createQuery(eventQuery)
                .setParameter("displayName", app.getDisplayName())
                .setParameter("applicationId", id)
                .executeUpdate();
        return rowCount;
    }

    @Transactional
    public boolean deleteApplication(UUID id) {
        String query = "DELETE FROM Application WHERE id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public EventType createEventType(EventType eventType) {
        Application app = entityManager.find(Application.class, eventType.getApplicationId());
        if (app == null) {
            throw new NotFoundException();
        } else {
            eventType.setApplication(app);
            entityManager.persist(eventType);
            eventType.filterOutApplication();
            return eventType;
        }
    }

    public List<Application> getApplications(String bundleName) {
        String sql = "FROM Application";
        if (bundleName != null) {
            sql += " WHERE bundle.name = :bundleName";
        }
        TypedQuery<Application> query = entityManager.createQuery(sql, Application.class);
        if (bundleName != null) {
            query = query.setParameter("bundleName", bundleName);
        }
        return query.getResultList();
    }

    public List<Application> getApplications(Collection<UUID> applicationIds) {
        String sql = "FROM Application WHERE id IN (:applicationIds)";
        return entityManager
                .createQuery(sql, Application.class)
                .setParameter("applicationIds", applicationIds)
                .getResultList();
    }

    public Application getApplication(UUID id) {
        return entityManager.find(Application.class, id);
    }

    public Application getApplication(String bundleName, String applicationName) {
        String query = "FROM Application WHERE bundle.name = :bundleName AND name = :applicationName";
        try {
            return entityManager.createQuery(query, Application.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public UUID getApplicationIdOfEventType(UUID eventTypeId) {
        String query = "SELECT application.id FROM EventType WHERE id = :id";
        return entityManager.createQuery(query, UUID.class)
                .setParameter("id", eventTypeId)
                .getSingleResult();
    }

    public EventType getEventType(String bundleName, String applicationName, String eventTypeName) {
        final String query = "FROM EventType WHERE name = :eventTypeName AND application.name = :applicationName AND application.bundle.name = :bundleName";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .getSingleResult();
    }

    public List<EventType> getEventTypes(UUID appId) {
        String query = "FROM EventType WHERE application.id = :appId";
        Application app = entityManager.find(Application.class, appId);
        if (app == null) {
            throw new NotFoundException();
        } else {
            List<EventType> eventTypes = entityManager.createQuery(query, EventType.class)
                    .setParameter("appId", appId)
                    .getResultList();
            for (EventType eventType : eventTypes) {
                eventType.filterOutApplication();
            }
            return eventTypes;
        }
    }

    @Transactional
    public int updateEventType(UUID id, EventType eventType) {
        String eventTypeQuery = "UPDATE EventType SET name = :name, displayName = :displayName, description = :description WHERE id = :id";
        int rowCount = entityManager.createQuery(eventTypeQuery)
                .setParameter("name", eventType.getName())
                .setParameter("displayName", eventType.getDisplayName())
                .setParameter("description", eventType.getDescription())
                .setParameter("id", id)
                .executeUpdate();
        String eventQuery = "UPDATE Event SET eventTypeDisplayName = :displayName WHERE eventType.id = :eventTypeId";
        entityManager.createQuery(eventQuery)
                .setParameter("displayName", eventType.getDisplayName())
                .setParameter("eventTypeId", id)
                .executeUpdate();
        return rowCount;
    }

    @Transactional
    public boolean deleteEventTypeById(UUID id) {
        String query = "DELETE FROM EventType WHERE id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    public List<EventType> getEventTypes(Query limiter, Set<UUID> appIds, UUID bundleId) {
        return getEventTypesQueryBuilder(appIds, bundleId)
                .join(JoinBuilder.builder().leftJoinFetch("e.application"))
                .limit(limiter != null ? limiter.getLimit() : null)
                .sort(limiter != null ? limiter.getSort() : null)
                .build(entityManager::createQuery)
                .getResultList();
    }

    public Long getEventTypesCount(Set<UUID> appIds, UUID bundleId) {
        return getEventTypesQueryBuilder(appIds, bundleId)
                .buildCount(entityManager::createQuery)
                .getSingleResult();
    }

    private QueryBuilder<EventType> getEventTypesQueryBuilder(Set<UUID> appIds, UUID bundleId) {
        return QueryBuilder
                .builder(EventType.class)
                .alias("e")
                .where(
                        WhereBuilder
                                .builder()
                                .ifAnd(appIds != null && appIds.size() > 0, "e.application.id IN (:appIds)", "appIds", appIds)
                                .ifAnd(bundleId != null, "e.application.bundle.id = :bundleId", "bundleId", bundleId)
                );
    }
}
