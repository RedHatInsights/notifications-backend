package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.DrawerNotification;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DrawerNotificationRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Integer updateReadStatus(String orgId, String username, Set<UUID> notificationIds, Boolean readStatus) {

        String hql = "UPDATE DrawerNotification SET read = :readStatus "
            + "WHERE orgId = :orgId and userId = :userId and id in (:notificationIds)";

        return entityManager.createQuery(hql)
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("readStatus", readStatus)
            .setParameter("notificationIds", notificationIds)
            .executeUpdate();
    }

    public List<DrawerEntryPayload> getNotifications(String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                              LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus, Query query) {
        query.setSortFields(DrawerNotification.SORT_FIELDS);
        query.setDefaultSortBy("created:DESC");
        Optional<Query.Sort> sort = query.getSort();

        String hql = "SELECT dn.id, dn.read, " +
            "dn.event.bundleDisplayName, dn.event.applicationDisplayName, dn.event.eventTypeDisplayName, dn.created, dn.event.renderedDrawerNotification "
            + "FROM DrawerNotification dn where dn.orgId = :orgId and dn.userId = :userid";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus);
        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(hql, Object[].class);
        setQueryParams(typedQuery, orgId, username, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus);

        Query.Limit limit = query.getLimit();

        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        List<Object[]> results = typedQuery.getResultList();
        return  results.stream().map(e -> new DrawerEntryPayload(e)).collect(Collectors.toList());
    }

    public Long count(String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                      LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus) {

        String hql = "SELECT count(dn.id) "
            + "FROM DrawerNotification dn where dn.orgId = :orgId and dn.userId = :userid";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus);

        TypedQuery<Long> typedQuery = entityManager.createQuery(hql, Long.class);
        setQueryParams(typedQuery, orgId, username, bundleIds, appIds, eventTypeIds, startDate, endDate, readStatus);

        return typedQuery.getSingleResult();
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                           LocalDateTime startDate, LocalDateTime endDate, Boolean readStatus) {

        if (eventTypeIds != null && !eventTypeIds.isEmpty()) {
            hql += " AND dn.event.eventType.id IN (:eventTypeIds)";
        } else if (appIds != null && !appIds.isEmpty()) {
            hql += " AND dn.event.applicationId IN (:appIds)";
        } else if (bundleIds != null && !bundleIds.isEmpty()) {
            hql += " AND dn.event.bundleId IN (:bundleIds)";
        }

        if (startDate != null && endDate != null) {
            hql += " AND dn.created BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND dn.created >= :startDate";
        } else if (endDate != null) {
            hql += " AND dn.created <= :endDate";
        }

        if (readStatus != null) {
            hql += " AND dn.read = :readStatus";
        }

        return hql;
    }

    private void setQueryParams(TypedQuery<?> query, String orgId, String username, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                LocalDateTime startDate, LocalDateTime endDate, Boolean status) {
        query.setParameter("orgId", orgId);
        query.setParameter("userid", username);

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

        if  (status != null) {
            query.setParameter("readStatus", status);
        }
    }

    private String getOrderBy(Query.Sort sort) {
        if (!sort.getSortColumn().equals("dn.event.created")) {
            return " " + sort.getSortQuery() + ", dn.event.created DESC";
        } else {
            return " " + sort.getSortQuery();
        }
    }

}
