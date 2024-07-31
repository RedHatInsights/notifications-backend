package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    public List<Event> getEvents(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                      Set<Boolean> invocationResults, boolean fetchNotificationHistory, Set<NotificationStatus> status, Query query) {
        query.setSortFields(Event.SORT_FIELDS);
        query.setDefaultSortBy("created:DESC");
        Optional<Query.Sort> sort = query.getSort();
        List<UUID> eventIds = getEventIds(orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, query);
        if (eventIds.isEmpty()) {
            return new ArrayList<>();
        }

        String hql;
        if (fetchNotificationHistory) {
            hql = "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.historyEntries he WHERE e.id IN (:eventIds)";
        } else {
            hql = "FROM Event e WHERE e.id IN (:eventIds)";
        }

        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        return entityManager.createQuery(hql, Event.class)
                .setParameter("eventIds", eventIds)
                .getResultList();
    }

    public Long count(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                      Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults,
                      Set<NotificationStatus> status) {
        String hql = "SELECT COUNT(*) FROM Event e WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status);

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        setQueryParams(query, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status);

        return query.getSingleResult();
    }

    private String getOrderBy(Query.Sort sort) {
        if (!sort.getSortColumn().equals("e.created")) {
            return " " + sort.getSortQuery() + ", e.created DESC";
        } else {
            return " " + sort.getSortQuery();
        }
    }

    private List<UUID> getEventIds(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                        LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                        Set<Boolean> invocationResults, Set<NotificationStatus> status, Query query) {
        String hql = "SELECT e.id FROM Event e WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status);
        Optional<Query.Sort> sort = query.getSort();

        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        TypedQuery<UUID> typedQuery = entityManager.createQuery(hql, UUID.class);
        setQueryParams(typedQuery, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status);

        Query.Limit limit = query.getLimit();

        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        return typedQuery.getResultList();
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                                           Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults,
                                           Set<NotificationStatus> status) {

        List<String> bundleOrAppsConditions = new ArrayList<>();

        if (bundleIds != null && !bundleIds.isEmpty()) {
            bundleOrAppsConditions.add("e.bundleId IN (:bundleIds)");
        }
        if (appIds != null && !appIds.isEmpty()) {
            bundleOrAppsConditions.add("e.applicationId IN (:appIds)");
        }

        if (bundleOrAppsConditions.size() > 0) {
            hql += " AND (" + String.join(" OR ", bundleOrAppsConditions) + ")";
        }

        if (eventTypeDisplayName != null) {
            hql += " AND LOWER(e.eventTypeDisplayName) LIKE :eventTypeDisplayName";
        }
        if (startDate != null && endDate != null) {
            hql += " AND e.created BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND e.created >= :startDate";
        } else if (endDate != null) {
            hql += " AND e.created <= :endDate";
        }

        boolean checkEndpointType = (endpointTypes != null && !endpointTypes.isEmpty()) || (compositeEndpointTypes != null && !compositeEndpointTypes.isEmpty());
        boolean checkInvocationResult = invocationResults != null && !invocationResults.isEmpty();
        boolean checkStatus = status != null && !status.isEmpty();
        if (checkEndpointType || checkInvocationResult || checkStatus) {
            List<String> subQueryConditions = new ArrayList<>();
            if (checkEndpointType) {
                List<String> subQueryEndpointTypes = new ArrayList<>();

                if (endpointTypes.size() > 0) {
                    subQueryEndpointTypes.add("nh.compositeEndpointType.type IN (:basicEndpointTypes)");
                }

                if (compositeEndpointTypes.size() > 0) {
                    subQueryEndpointTypes.add("nh.compositeEndpointType IN (:compositeEndpointTypes)");
                }

                subQueryConditions.add("(" + String.join(" OR ", subQueryEndpointTypes) + ")");
            }

            if (checkInvocationResult) {
                subQueryConditions.add("nh.invocationResult IN (:invocationResults)");
            }

            if (checkStatus) {
                subQueryConditions.add("nh.status IN (:status)");
            }

            hql += " AND EXISTS (SELECT 1 FROM NotificationHistory nh WHERE nh.event = e AND " + String.join(" AND ", subQueryConditions) + ")";
        }

        return hql;
    }

    private void setQueryParams(TypedQuery<?> query, String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
                                       LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                       Set<Boolean> invocationResults, Set<NotificationStatus> status) {
        query.setParameter("orgId", orgId);
        if (bundleIds != null && !bundleIds.isEmpty()) {
            query.setParameter("bundleIds", bundleIds);
        }
        if (appIds != null && !appIds.isEmpty()) {
            query.setParameter("appIds", appIds);
        }
        if (eventTypeName != null) {
            query.setParameter("eventTypeDisplayName", "%" + eventTypeName.toLowerCase() + "%");
        }
        if (startDate != null) {
            query.setParameter("startDate", Timestamp.valueOf(startDate.atStartOfDay()));
        }
        if (endDate != null) {
            query.setParameter("endDate", Timestamp.valueOf(endDate.atTime(LocalTime.MAX))); // at end of day
        }
        if (endpointTypes != null && !endpointTypes.isEmpty()) {
            query.setParameter("basicEndpointTypes", endpointTypes);
        }
        if (compositeEndpointTypes != null && !compositeEndpointTypes.isEmpty()) {
            query.setParameter("compositeEndpointTypes", compositeEndpointTypes);
        }
        if (invocationResults != null && !invocationResults.isEmpty()) {
            query.setParameter("invocationResults", invocationResults);
        }
        if  (status != null && !status.isEmpty()) {
            query.setParameter("status", status);
        }
    }
}
