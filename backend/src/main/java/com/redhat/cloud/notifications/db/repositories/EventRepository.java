package com.redhat.cloud.notifications.db.repositories;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import static com.redhat.cloud.notifications.routers.EventResource.SORT_BY_PATTERN;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    public List<Event> getEvents(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults,
                                      boolean fetchNotificationHistory, Integer limit, Integer offset, String sortBy) {
        Optional<String> orderByCondition = getOrderByCondition(sortBy);
        List<UUID> eventIds = getEventIds(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, limit, offset, orderByCondition);
        String hql;
        if (fetchNotificationHistory) {
            hql = "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.historyEntries he WHERE e.id IN (:eventIds)";
        } else {
            hql = "FROM Event e WHERE e.id IN (:eventIds)";
        }

        if (orderByCondition.isPresent()) {
            hql += orderByCondition.get();
        }

        return entityManager.createQuery(hql, Event.class)
                .setParameter("eventIds", eventIds)
                .getResultList();
    }

    public Long count(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        String hql = "SELECT COUNT(*) FROM Event e WHERE e.accountId = :accountId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        return query.getSingleResult();
    }

    public Event getEventById(UUID eventId, boolean fetchNotificationHistory) {

        String hql;

        if (fetchNotificationHistory) {
            hql = "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.historyEntries he WHERE e.id = :eventId ";
        } else {
            hql = "FROM Event e WHERE e.id = :eventId";
        }

        return entityManager.createQuery(hql, Event.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }

    private Optional<String> getOrderByCondition(String sortBy) {
        if (sortBy == null) {
            return Optional.of(" ORDER BY e.created DESC");
        } else {
            Matcher sortByMatcher = SORT_BY_PATTERN.matcher(sortBy);
            if (sortByMatcher.matches()) {
                String sortField = getSortField(sortByMatcher.group(1));
                String sortDirection = sortByMatcher.group(2);
                String orderBy = " ORDER BY " + sortField + " " + sortDirection;
                if (!sortField.equals("e.created")) {
                    orderBy += ", e.created DESC";
                }
                return Optional.of(orderBy);
            } else {
                return Optional.empty();
            }
        }
    }

    private List<UUID> getEventIds(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                        LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults,
                                        Integer limit, Integer offset, Optional<String> orderByCondition) {
        String hql = "SELECT e.id FROM Event e WHERE e.accountId = :accountId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        if (orderByCondition.isPresent()) {
            hql += orderByCondition.get();
        }

        TypedQuery<UUID> query = entityManager.createQuery(hql, UUID.class);
        setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        if (limit != null) {
            query.setMaxResults(limit);
        }
        if (offset != null) {
            query.setFirstResult(offset);
        }

        return query.getResultList();
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        if (bundleIds != null && !bundleIds.isEmpty()) {
            hql += " AND e.bundleId IN (:bundleIds)";
        }
        if (appIds != null && !appIds.isEmpty()) {
            hql += " AND e.applicationId IN (:appIds)";
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

        boolean checkEndpointType = endpointTypes != null && !endpointTypes.isEmpty();
        boolean checkInvocationResult = invocationResults != null && !invocationResults.isEmpty();
        if (checkEndpointType || checkInvocationResult) {
            List<String> subQueryConditions = new ArrayList<>();
            if (checkEndpointType) {
                subQueryConditions.add("nh.endpointType IN (:endpointTypes)");
            }
            if (checkInvocationResult) {
                subQueryConditions.add("nh.invocationResult IN (:invocationResults)");
            }
            hql += " AND EXISTS (SELECT 1 FROM NotificationHistory nh WHERE nh.event = e AND " + String.join(" AND ", subQueryConditions) + ")";
        }

        return hql;
    }

    private static void setQueryParams(TypedQuery<?> query, String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
                                       LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        query.setParameter("accountId", accountId);
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
            query.setParameter("endpointTypes", endpointTypes);
        }
        if (invocationResults != null && !invocationResults.isEmpty()) {
            query.setParameter("invocationResults", invocationResults);
        }
    }

    private static String getSortField(String field) {
        switch (field) {
            case "bundle":
                return "e.bundleDisplayName";
            case "application":
                return "e.applicationDisplayName";
            case "event":
                return "e.eventTypeDisplayName";
            case "created":
                return "e.created";
            default:
                throw new IllegalArgumentException("Unknown sort field: " + field);
        }
    }
}
