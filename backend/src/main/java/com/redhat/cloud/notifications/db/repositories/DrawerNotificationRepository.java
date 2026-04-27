package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.Sort;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Transactional
    public Integer updateReadStatus(String orgId, String username, Set<UUID> notificationIds, Boolean readStatus) {
        if (readStatus) {
            // Mark as read: INSERT into drawer_read_status
            // Use unnest to insert multiple rows from the Set<UUID>
            String insertSql = """
                INSERT INTO drawer_read_status (org_id, user_id, event_id)
                SELECT :orgId, :userId, event_id
                FROM unnest(CAST(:notificationIds AS uuid[])) AS event_id
                ON CONFLICT (org_id, user_id, event_id) DO NOTHING
                """;

            return entityManager.createNativeQuery(insertSql)
                .setParameter("orgId", orgId)
                .setParameter("userId", username)
                .setParameter("notificationIds", notificationIds.toArray(new UUID[0]))
                .executeUpdate();
        } else {
            // Mark as unread: DELETE from drawer_read_status
            String deleteSql = """
                DELETE FROM drawer_read_status
                WHERE org_id = :orgId
                  AND user_id = :userId
                  AND event_id = ANY(:notificationIds)
                """;

            return entityManager.createNativeQuery(deleteSql)
                .setParameter("orgId", orgId)
                .setParameter("userId", username)
                .setParameter("notificationIds", notificationIds.toArray(new UUID[0]))
                .executeUpdate();
        }
    }

    public List<DrawerEntryPayload> getNotifications(String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                              LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus, Query query, List<UUID> excludedEventIds) {

        Set<UUID> subscribedEventTypes = getSubscribedEventTypes(orgId, username);

        if (subscribedEventTypes.isEmpty()) {
            return new ArrayList<>();
        }

        Optional<Sort> sort = Sort.getSort(query, "created:DESC", Event.getSortFields(true));

        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypesNotEmpty = eventTypeIds != null && !eventTypeIds.isEmpty();
        boolean excludeNotEmpty = excludedEventIds != null && !excludedEventIds.isEmpty();

        String hql = buildBaseHql(false);
        hql = addHqlConditions(hql, bundlesNotEmpty, applicationsNotEmpty, eventTypesNotEmpty, excludeNotEmpty, startDate, endDate, readStatus);

        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(hql, Object[].class);
        setQueryParams(typedQuery, orgId, username, subscribedEventTypes, bundleIds, appIds, eventTypeIds, excludedEventIds, startDate, endDate);

        Query.Limit limit = query.getLimit();
        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        List<Object[]> results = typedQuery.getResultList();
        return results.stream().map(DrawerEntryPayload::new).collect(Collectors.toList());
    }

    public Long count(String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                      LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus, List<UUID> excludedEventIds) {

        Set<UUID> subscribedEventTypes = getSubscribedEventTypes(orgId, username);

        if (subscribedEventTypes.isEmpty()) {
            return 0L;
        }

        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypesNotEmpty = eventTypeIds != null && !eventTypeIds.isEmpty();
        boolean excludeNotEmpty = excludedEventIds != null && !excludedEventIds.isEmpty();

        String hql = buildBaseHql(true);
        hql = addHqlConditions(hql, bundlesNotEmpty, applicationsNotEmpty, eventTypesNotEmpty, excludeNotEmpty, startDate, endDate, readStatus);

        TypedQuery<Long> typedQuery = entityManager.createQuery(hql, Long.class);
        setQueryParams(typedQuery, orgId, username, subscribedEventTypes, bundleIds, appIds, eventTypeIds, excludedEventIds, startDate, endDate);

        return typedQuery.getSingleResult();
    }

    public Set<UUID> getSubscribedEventTypes(String orgId, String username) {
        // Get all drawer event type IDs
        Set<UUID> allDrawerEventTypes = eventTypeRepository.getEventTypeIdsByIncludedInDrawer();

        // Get user's unsubscriptions (DRAWER is subscribe-by-default)
        Set<UUID> unsubscribedEventTypes = subscriptionRepository.getUnsubscribedDrawerEventTypeIds(orgId, username);

        // Calculate subscribed event types (all - unsubscribed)
        Set<UUID> subscribedEventTypes = new HashSet<>(allDrawerEventTypes);
        subscribedEventTypes.removeAll(unsubscribedEventTypes);
        return subscribedEventTypes;
    }


    private String buildBaseHql(boolean isCountQuery) {
        if (isCountQuery) {
            return "SELECT COUNT(e.id) FROM Event e " +
                "JOIN e.eventType et " +
                "JOIN et.application app " +
                "JOIN app.bundle bundle " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND e.renderedDrawerNotification IS NOT NULL AND et.id IN (:subscribedEventTypes)";
        } else {
            return "SELECT e.id, " +
                "drs.id IS NOT NULL, " +
                "bundle.displayName, app.displayName, et.displayName, e.created, e.renderedDrawerNotification, bundle.name, e.severity " +
                "FROM Event e " +
                "JOIN e.eventType et " +
                "JOIN et.application app " +
                "JOIN app.bundle bundle " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND e.renderedDrawerNotification IS NOT NULL AND et.id IN (:subscribedEventTypes)";
        }
    }

    private String getOrderBy(Sort sort) {
        if (!sort.getSortColumn().equals("e.created")) {
            return " " + sort.getSortQuery() + ", e.created DESC";
        } else {
            return " " + sort.getSortQuery();
        }
    }

    private static String addHqlConditions(String hql,
                                           boolean bundlesNotEmpty, boolean applicationsNotEmpty, boolean eventTypesNotEmpty,
                                           boolean excludeNotEmpty, LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus) {
        // Exclusion list from Kessel check
        if (excludeNotEmpty) {
            hql += " AND e.id NOT IN (:uuidToExclude)";
        }

        // Date range filtering
        if (startDate != null && endDate != null) {
            hql += " AND e.created BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND e.created >= :startDate";
        } else if (endDate != null) {
            hql += " AND e.created <= :endDate";
        }

        // Read status filtering (via LEFT JOIN, not post-query - critical for pagination!)
        if (readStatus != null) {
            if (readStatus) {
                hql += " AND drs.id IS NOT NULL";
            } else {
                hql += " AND drs.id IS NULL";
            }
        }

        // Additional filter by event type IDs (on top of subscription filter)
        if (eventTypesNotEmpty) {
            hql += " AND et.id IN (:eventTypeIds)";
        } else if (applicationsNotEmpty) {
            hql += " AND app.id IN (:appIds)";
        } else if (bundlesNotEmpty) {
            hql += " AND bundle.id IN (:bundleIds)";
        }

        return hql;
    }

    private void setQueryParams(TypedQuery<?> query, String orgId, String username, Set<UUID> subscribedEventTypes,
                                Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                List<UUID> uuidToExclude, LocalDateTime startDate, LocalDateTime endDate) {
        //readStatus is handled in SQL via IS NULL / IS NOT NULL, not as a parameter
        query.setParameter("orgId", orgId);
        query.setParameter("userid", username);
        query.setParameter("subscribedEventTypes", subscribedEventTypes);

        if (uuidToExclude != null && !uuidToExclude.isEmpty()) {
            query.setParameter("uuidToExclude", uuidToExclude);
        }

        if (eventTypeIds != null && !eventTypeIds.isEmpty()) {
            query.setParameter("eventTypeIds", eventTypeIds);
        } else if (appIds != null && !appIds.isEmpty()) {
            query.setParameter("appIds", appIds);
        } else if (bundleIds != null && !bundleIds.isEmpty()) {
            query.setParameter("bundleIds", bundleIds);
        }

        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
    }

    @Transactional
    public void cleanupIntegrations(int limit) {
        String deleteQuery = "delete from endpoints where id in (select id from endpoints where endpoint_type_v2 = 'DRAWER' " +
            "and org_id is not null and not exists (select 1 from endpoint_event_type where endpoint_id = id) limit :limit)";
        entityManager.createNativeQuery(deleteQuery)
            .setParameter("limit", limit)
            .executeUpdate();
    }
}
