package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.Sort;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.routers.handlers.event.EventAuthorizationCriterion;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.SecurityContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    BackendConfig backendConfig;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @Transactional
    public Integer updateReadStatus(String orgId, String username, Set<UUID> notificationIds, Boolean readStatus) {
        if (readStatus) {
            // Mark as read: INSERT into drawer_read_status
            // Use unnest to insert multiple rows from the Set<UUID>
            String insertSql = """
                INSERT INTO drawer_read_status (org_id, user_id, event_id, read_at)
                SELECT :orgId, :userId, event_id, NOW()
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

    public List<DrawerEntryPayload> getNotifications(SecurityContext securityContext, String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                              LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus, Query query) { //todo jbonsch understand

        boolean useNormalized = backendConfig.isNormalizedQueriesEnabled(orgId);

        // Step 1: Get all drawer event type IDs
        Set<UUID> allDrawerEventTypes = eventTypeRepository.getEventTypeIdsByIncludedInDrawer();

        // Step 2: Get user's unsubscriptions (DRAWER is subscribe-by-default)
        Set<UUID> unsubscribedEventTypes = subscriptionRepository.getUnsubscribedDrawerEventTypeIds(orgId, username);

        // Step 3: Calculate subscribed event types (all - unsubscribed)
        Set<UUID> subscribedEventTypes = new HashSet<>(allDrawerEventTypes);
        subscribedEventTypes.removeAll(unsubscribedEventTypes);

        // If user unsubscribed from everything, return empty
        if (subscribedEventTypes.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 4: Convert LocalDateTime to LocalDate (EventRepository convention)
        LocalDate startLocalDate = startDate != null ? startDate.toLocalDate() : null;
        LocalDate endLocalDate = endDate != null ? endDate.toLocalDate() : null;

        // Step 5: Get events with authorization criteria (copy event log RBAC pattern)
        List<UUID> uuidToExclude = new ArrayList<>();
        if (backendConfig.isKesselChecksOnEventLogEnabled(orgId)) {
            Log.info("Check for drawer events with authorization criterion");
            List<EventAuthorizationCriterion> listEventsAuthCriterion = eventRepository.getDrawerEventsWithCriterion(
                orgId, subscribedEventTypes, startLocalDate, endLocalDate
            );

            // Step 6: Check Kessel authorization with caching (by criterion hashCode)
            Map<Integer, Boolean> criterionResultCache = new HashMap<>();
            for (EventAuthorizationCriterion eventAuthorizationCriterion : listEventsAuthCriterion) {
                int criterionHashCode = eventAuthorizationCriterion.authorizationCriterion().hashCode();
                if (!criterionResultCache.containsKey(criterionHashCode)) {
                    criterionResultCache.put(criterionHashCode, kesselInventoryAuthorization.hasPermissionOnResource(securityContext, eventAuthorizationCriterion.authorizationCriterion()));
                }
                if (!criterionResultCache.get(criterionHashCode)) {
                    Log.infof("%s is not visible for current user", eventAuthorizationCriterion.id());
                    uuidToExclude.add(eventAuthorizationCriterion.id());
                }
            }
        }

        // Step 7: Query full events with read status (LEFT JOIN on drawer_read_status)
        Optional<Sort> sort = Sort.getSort(query, "created:DESC", Event.getSortFields(useNormalized));

        // Calculate filter flags
        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypesNotEmpty = eventTypeIds != null && !eventTypeIds.isEmpty();
        boolean excludeNotEmpty = !uuidToExclude.isEmpty();

        String hql;
        if (useNormalized) {
            hql = "SELECT e.id, " +
                "CASE WHEN drs.readAt IS NOT NULL THEN true ELSE false END, " +
                "bundle.displayName, app.displayName, et.displayName, e.created, e.renderedDrawerNotification, bundle.name, e.severity " +
                "FROM Event e " +
                "JOIN e.eventType et " +
                "JOIN et.application app " +
                "JOIN app.bundle bundle " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.orgId = :orgId AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND et.includedInDrawer = true AND et.id IN (:subscribedEventTypes)";
        } else {
            hql = "SELECT e.id, " +
                "CASE WHEN drs.readAt IS NOT NULL THEN true ELSE false END, " +
                "e.bundleDisplayName, e.applicationDisplayName, e.eventTypeDisplayName, e.created, e.renderedDrawerNotification, bundle.name, e.severity " +
                "FROM Event e " +
                "JOIN Bundle bundle ON e.bundleId = bundle.id " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.orgId = :orgId AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND e.eventType.includedInDrawer = true AND e.eventType.id IN (:subscribedEventTypes)";
        }

        // Add filters
        hql = addHqlConditions(hql, useNormalized, bundlesNotEmpty, applicationsNotEmpty, eventTypesNotEmpty, excludeNotEmpty, startDate, endDate, readStatus);

        if (sort.isPresent()) {
            hql += " " + sort.get().getSortQuery();
        }

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(hql, Object[].class);
        setQueryParams(typedQuery, orgId, username, subscribedEventTypes, bundleIds, appIds, eventTypeIds, uuidToExclude, startDate, endDate);

        Query.Limit limit = query.getLimit();
        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        List<Object[]> results = typedQuery.getResultList();
        return results.stream().map(DrawerEntryPayload::new).collect(Collectors.toList());
    }

    public Long count(SecurityContext securityContext, String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                      LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus) { // todo jbonsch understand
        boolean useNormalized = backendConfig.isNormalizedQueriesEnabled(orgId);

        // Step 1: Get all drawer event type IDs
        Set<UUID> allDrawerEventTypes = eventTypeRepository.getEventTypeIdsByIncludedInDrawer();

        // Step 2: Get user's unsubscriptions (DRAWER is subscribe-by-default)
        Set<UUID> unsubscribedEventTypes = subscriptionRepository.getUnsubscribedDrawerEventTypeIds(orgId, username);

        // Step 3: Calculate subscribed event types (all - unsubscribed)
        Set<UUID> subscribedEventTypes = new HashSet<>(allDrawerEventTypes);
        subscribedEventTypes.removeAll(unsubscribedEventTypes);

        // If user unsubscribed from everything, return 0
        if (subscribedEventTypes.isEmpty()) {
            return 0L;
        }

        // Step 4: Convert LocalDateTime to LocalDate (EventRepository convention)
        LocalDate startLocalDate = startDate != null ? startDate.toLocalDate() : null;
        LocalDate endLocalDate = endDate != null ? endDate.toLocalDate() : null;

        // Step 5: Get events with authorization criteria (copy event log RBAC pattern)
        List<UUID> uuidToExclude = new ArrayList<>();
        if (backendConfig.isKesselChecksOnEventLogEnabled(orgId)) {
            Log.info("Check for drawer events with authorization criterion (count)");
            List<EventAuthorizationCriterion> listEventsAuthCriterion = eventRepository.getDrawerEventsWithCriterion(
                orgId, subscribedEventTypes, startLocalDate, endLocalDate
            );

            // Step 6: Check Kessel authorization with caching (by criterion hashCode)
            Map<Integer, Boolean> criterionResultCache = new HashMap<>();
            for (EventAuthorizationCriterion eventAuthorizationCriterion : listEventsAuthCriterion) {
                int criterionHashCode = eventAuthorizationCriterion.authorizationCriterion().hashCode();
                if (!criterionResultCache.containsKey(criterionHashCode)) {
                    criterionResultCache.put(criterionHashCode, kesselInventoryAuthorization.hasPermissionOnResource(securityContext, eventAuthorizationCriterion.authorizationCriterion()));
                }
                if (!criterionResultCache.get(criterionHashCode)) {
                    Log.infof("%s is not visible for current user (count)", eventAuthorizationCriterion.id());
                    uuidToExclude.add(eventAuthorizationCriterion.id());
                }
            }
        }

        // Step 7: Count events with same filters as getNotifications()
        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypesNotEmpty = eventTypeIds != null && !eventTypeIds.isEmpty();
        boolean excludeNotEmpty = !uuidToExclude.isEmpty();

        String hql;
        if (useNormalized) {
            hql = "SELECT COUNT(e.id) FROM Event e " +
                "JOIN e.eventType et " +
                "JOIN et.application app " +
                "JOIN app.bundle bundle " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.orgId = :orgId AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND et.includedInDrawer = true AND et.id IN (:subscribedEventTypes)";
        } else {
            hql = "SELECT COUNT(e.id) FROM Event e " +
                "JOIN Bundle bundle ON e.bundleId = bundle.id " +
                "LEFT JOIN DrawerReadStatus drs ON drs.id.eventId = e.id AND drs.id.orgId = :orgId AND drs.id.userId = :userid " +
                "WHERE e.orgId = :orgId AND e.eventType.includedInDrawer = true AND e.eventType.id IN (:subscribedEventTypes)";
        }

        // Add filters (same as getNotifications)
        hql = addHqlConditions(hql, useNormalized, bundlesNotEmpty, applicationsNotEmpty, eventTypesNotEmpty, excludeNotEmpty, startDate, endDate, readStatus);

        TypedQuery<Long> typedQuery = entityManager.createQuery(hql, Long.class);
        setQueryParams(typedQuery, orgId, username, subscribedEventTypes, bundleIds, appIds, eventTypeIds, uuidToExclude, startDate, endDate);

        return typedQuery.getSingleResult();
    }

    private String getOrderBy(Sort sort) {
        if (!sort.getSortColumn().equals("dn.created")) {
            return " " + sort.getSortQuery() + ", dn.created DESC";
        } else {
            return " " + sort.getSortQuery();
        }
    }

    private static String addHqlConditions(String hql, boolean useNormalized,
                                           boolean bundlesNotEmpty, boolean applicationsNotEmpty, boolean eventTypesNotEmpty,
                                           boolean excludeNotEmpty, LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus) {

        // Exclusion list from RBAC check
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
                hql += " AND drs.readAt IS NOT NULL";
            } else {
                hql += " AND drs.readAt IS NULL";
            }
        }

        // Additional filter by event type IDs (on top of subscription filter)
        if (eventTypesNotEmpty) {
            if (useNormalized) {
                hql += " AND et.id IN (:eventTypeIds)";
            } else {
                hql += " AND e.eventType.id IN (:eventTypeIds)";
            }
        } else if (applicationsNotEmpty) {
            if (useNormalized) {
                hql += " AND app.id IN (:appIds)";
            } else {
                hql += " AND e.applicationId IN (:appIds)";
            }
        } else if (bundlesNotEmpty) {
            if (useNormalized) {
                hql += " AND bundle.id IN (:bundleIds)";
            } else {
                hql += " AND e.bundleId IN (:bundleIds)";
            }
        }

        return hql;
    }

    private void setQueryParams(TypedQuery<?> query, String orgId, String username, Set<UUID> subscribedEventTypes,
                                Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                List<UUID> uuidToExclude, LocalDateTime startDate, LocalDateTime endDate) {
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

        // Note: readStatus is handled in SQL via IS NULL / IS NOT NULL, not as a parameter
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
