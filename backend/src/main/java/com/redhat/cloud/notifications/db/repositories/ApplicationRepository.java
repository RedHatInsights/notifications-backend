package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.builder.JoinBuilder;
import com.redhat.cloud.notifications.db.builder.QueryBuilder;
import com.redhat.cloud.notifications.db.builder.WhereBuilder;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class ApplicationRepository {

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

    /**
     * Updates the given application and its role access.
     * @param application the application to be updated.
     * @param internalRoleAccess the role to be updated.
     */
    @Transactional
    public void updateApplicationAndAccess(final Application application, final InternalRoleAccess internalRoleAccess) {
        // First update the internal role of the application.
        final String internalRole = internalRoleAccess.getRole();
        if (internalRole != null && !internalRole.isBlank()) {
            final String updateRoleQuery =
                "UPDATE " +
                    "InternalRoleAccess " +
                    "SET " +
                    "role = :role " +
                    "WHERE " +
                    "application = :application";

            this.entityManager
                .createQuery(updateRoleQuery)
                .setParameter("role", internalRoleAccess.getRole())
                .setParameter("application", application)
                .executeUpdate();
        }

        // And second update the application itself.
        final String updateApplicationQuery =
            "UPDATE " +
                "Application " +
            "SET " +
                "name = :name, " +
                "displayName = :displayName " +
            "WHERE " +
                "id = :uuid";

        this.entityManager
            .createQuery(updateApplicationQuery)
            .setParameter("name", application.getName())
            .setParameter("displayName", application.getDisplayName())
            .setParameter("uuid", application.getId())
            .executeUpdate();
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

    public List<Application> getApplications(@NotNull UUID bundleId) {
        String sql = "FROM Application WHERE bundle.id = :bundleId";
        return entityManager.createQuery(sql, Application.class)
                .setParameter("bundleId", bundleId)
                .getResultList();
    }

    public List<Application> getApplications(String bundleName) {
        String sql = "FROM Application";
        if (bundleName != null) {
            sql += " WHERE bundle.name = :bundleName";
        }
        sql += " ORDER BY displayName ASC";
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

    /**
     * Returns applications that have at least 1 forced email notification in a given bundle
     */
    public List<Application> getApplicationsWithForcedEmail(UUID bundleId, String orgId) {
        String query = "SELECT a FROM Application a " +
                "JOIN a.eventTypes et " +
                "JOIN et.behaviors etb " +
                "JOIN etb.behaviorGroup.actions action " +
                "WHERE a.bundle.id = :bundleId AND (etb.behaviorGroup.orgId is NULL OR etb.behaviorGroup.orgId = :orgId) " +
                "AND EXISTS (" +
                    "SELECT 1 FROM SystemSubscriptionProperties props " +
                    "WHERE action.id.endpointId = props.id AND props.ignorePreferences = true" +
                ")";
        return entityManager.createQuery(query, Application.class)
                .setParameter("bundleId", bundleId)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    public UUID getApplicationIdOfEventType(UUID eventTypeId) {
        String query = "SELECT application.id FROM EventType WHERE id = :id";
        return entityManager.createQuery(query, UUID.class)
                .setParameter("id", eventTypeId)
                .getSingleResult();
    }

    public EventType getEventType(String bundleName, String applicationName, String eventTypeName) {
        final String query = "FROM EventType WHERE name = :eventTypeName AND application.name = :applicationName AND application.bundle.name = :bundleName";
        try {
            return entityManager.createQuery(query, EventType.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("applicationName", applicationName)
                    .setParameter("eventTypeName", eventTypeName)
                    .getSingleResult();
        } catch (NoResultException noResultException) {
            return null;
        }
    }

    public Map<String, Map<String, List<String>>> getBaetList() {
        final String query = "SELECT application.bundle.name, application.name, name FROM EventType";
        final List<String[]> flatBaet = entityManager.createQuery(query, String[].class)
            .getResultList();

        final Map<String, Map<String, List<String>>> mapBaet = new HashMap<>();
        flatBaet.stream().forEach(baet -> mapBaet.computeIfAbsent(baet[0], k -> new HashMap<>()).computeIfAbsent(baet[1], k -> new ArrayList<>()).add(baet[2]));
        return mapBaet;
    }

    public List<EventType> getEventTypes(UUID appId) {
        String query = "FROM EventType WHERE application.id = :appId order by name";
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
        String eventTypeQuery = "UPDATE EventType SET name = :name, displayName = :displayName, description = :description, fullyQualifiedName = :fullyQualifiedName, subscribedByDefault = :subscribedByDefault, subscriptionLocked = :subscriptionLocked, visible = :visible, restrictToRecipientsIntegrations = :restrictToRecipientsIntegrations WHERE id = :id";
        int rowCount = entityManager.createQuery(eventTypeQuery)
                .setParameter("name", eventType.getName())
                .setParameter("fullyQualifiedName", eventType.getFullyQualifiedName())
                .setParameter("displayName", eventType.getDisplayName())
                .setParameter("description", eventType.getDescription())
                .setParameter("subscribedByDefault", eventType.isSubscribedByDefault())
                .setParameter("subscriptionLocked", eventType.isSubscriptionLocked())
                .setParameter("visible", eventType.isVisible())
                .setParameter("restrictToRecipientsIntegrations", eventType.isRestrictToRecipientsIntegrations())
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
    public int updateEventTypeVisibility(UUID id, boolean isVisible) {
        String eventTypeQuery = "UPDATE EventType SET visible = :visible WHERE id = :id";
        int rowCount = entityManager.createQuery(eventTypeQuery)
            .setParameter("visible", isVisible)
            .setParameter("id", id)
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

    public List<EventType> getEventTypes(Query limiter, Set<UUID> appIds, UUID bundleId, String eventTypeName, boolean excludeMutedTypes, List<UUID> unmutedEventTypeIds) {
        if (limiter != null) {
            limiter.setSortFields(EventType.SORT_FIELDS);
        }

        return getEventTypesQueryBuilder(appIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds)
                .join(JoinBuilder.builder().leftJoinFetch("e.application"))
                .limit(limiter != null ? limiter.getLimit() : null)
                .sort(limiter != null ? limiter.getSort() : null)
                .build(entityManager::createQuery)
                .getResultList();
    }

    public Long getEventTypesCount(Set<UUID> appIds, UUID bundleId, String eventTypeName, boolean excludeMutedTypes, List<UUID> unmutedEventTypeIds) {
        return getEventTypesQueryBuilder(appIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds)
                .buildCount(entityManager::createQuery)
                .getSingleResult();
    }

    /**
     * Checks whether the "application" — "bundle" combination specified exists.
     * @param applicationName the name of the application.
     * @param bundleName the name of the bundle.
     * @return true if the combination exists, false otherwise.
     */
    public boolean applicationBundleExists(final String applicationName, final String bundleName) {
        final String applicationBundleExistsQuery =
            "SELECT " +
                "1 " +
            "FROM " +
                "Application AS a " +
            "INNER JOIN " +
                "Bundle AS b" +
                    " ON b = a.bundle " +
            "WHERE " +
                "a.name = :application_name " +
            "AND " +
                "b.name = :bundle_name";

        try {
            this.entityManager
                    .createQuery(applicationBundleExistsQuery)
                    .setParameter("application_name", applicationName)
                    .setParameter("bundle_name", bundleName)
                    .getSingleResult();

            return true;
        } catch (final NoResultException e) {
            return false;
        }
    }

    private QueryBuilder<EventType> getEventTypesQueryBuilder(Set<UUID> appIds, UUID bundleId, String eventTypeName, boolean excludeMutedTypes, List<UUID> unmutedEventTypeIds) {
        return QueryBuilder
                .builder(EventType.class)
                .alias("e")
                .where(
                        WhereBuilder
                                .builder()
                                .ifAnd(appIds != null && appIds.size() > 0, "e.application.id IN (:appIds)", "appIds", appIds)
                                .ifAnd(bundleId != null, "e.application.bundle.id = :bundleId", "bundleId", bundleId)
                                .ifAnd(eventTypeName != null, "(LOWER(e.displayName) LIKE :eventTypeName OR LOWER(e.name) LIKE :eventTypeName)", "eventTypeName", (Supplier<String>) () -> "%" + eventTypeName.toLowerCase() + "%")
                                .ifAnd(excludeMutedTypes, "e.id IN (:unmutedEventTypeIds)", "unmutedEventTypeIds", unmutedEventTypeIds)
                                .and("e.visible = true")
                );
    }
}
