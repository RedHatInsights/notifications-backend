package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    private final Map<String, String> sortFields = Map.of(
            "bundle", "e.bundleDisplayName",
            "application", "e.applicationDisplayName",
            "event", "e.eventTypeDisplayName",
            "created", "e.created"
    );

    public List<Event> getEvents(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                      Set<Boolean> invocationResults, boolean fetchNotificationHistory, Query query) {
        query.setSortFields(sortFields);
        Optional<Query.Sort> sort = query.getSort();
        List<UUID> eventIds = getEventIds(orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, query);
        String hql;
        if (fetchNotificationHistory) {
            hql = "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.historyEntries he WHERE e.id IN (:eventIds)";
        } else {
            hql = "FROM Event e WHERE e.id IN (:eventIds)";
        }

        if (sort.isPresent()) {
            hql += sort.get().getSortQuery();
        }

        return entityManager.createQuery(hql, Event.class)
                .setParameter("eventIds", eventIds)
                .getResultList();
    }

    public Long count(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                      Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults) {
        String hql = "SELECT COUNT(*) FROM Event e WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults);

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        setQueryParams(query, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults);

        return query.getSingleResult();
    }

    private List<UUID> getEventIds(String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                        LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                        Set<Boolean> invocationResults, Query query) {
        String hql = "SELECT e.id FROM Event e WHERE ";
        if (featureFlipper.isUseOrgIdInEvents()) {
            hql += "e.orgId = :orgId";
        } else {
            hql += "e.accountId = :accountId";
        }

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults);
        Optional<Query.Sort> sort = query.getSort();

        if (sort.isPresent()) {
            hql += sort.get().getSortQuery();
        }

        TypedQuery<UUID> typedQuery = entityManager.createQuery(hql, UUID.class);
        setQueryParams(typedQuery, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults);

        Query.Limit limit = query.getLimit();

        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        return typedQuery.getResultList();
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                                           Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults) {

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
        if (checkEndpointType || checkInvocationResult) {
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
            hql += " AND EXISTS (SELECT 1 FROM NotificationHistory nh WHERE nh.event = e AND " + String.join(" AND ", subQueryConditions) + ")";
        }

        return hql;
    }

    private void setQueryParams(TypedQuery<?> query, String orgId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
                                       LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                       Set<Boolean> invocationResults) {
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
    }
}
