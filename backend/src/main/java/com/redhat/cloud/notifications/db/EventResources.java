package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import static com.redhat.cloud.notifications.routers.EventService.SORT_BY_PATTERN;

@ApplicationScoped
public class EventResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    // Note: This method uses a stateless session
    public Uni<Event> create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(event)
                    .replaceWith(event);
        });
    }

    public Uni<List<Event>> getEvents(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults,
                                      Integer limit, Integer offset, String sortBy) {
        return sessionFactory.withSession(session -> {
            Optional<String> orderByCondition = getOrderByCondition(sortBy);
            return getEventIds(accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, limit, offset, orderByCondition)
                    .onItem().transformToUni(eventIds -> {
                        String hql = "SELECT DISTINCT e FROM Event e " +
                                "JOIN FETCH e.eventType et JOIN FETCH et.application a JOIN FETCH a.bundle b " +
                                "LEFT JOIN FETCH e.historyEntries he " +
                                "WHERE e.accountId = :accountId AND e.id IN (:eventIds)";

                        if (orderByCondition.isPresent()) {
                            hql += orderByCondition.get();
                        }

                        return session.createQuery(hql, Event.class)
                                .setParameter("accountId", accountId)
                                .setParameter("eventIds", eventIds)
                                .getResultList();
                    });
        });
    }

    public Uni<Long> count(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        return sessionFactory.withSession(session -> {
            String hql = "SELECT COUNT(*) FROM Event e JOIN e.eventType et JOIN et.application a JOIN a.bundle b " +
                    "WHERE e.accountId = :accountId";

            hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

            Mutiny.Query<Long> query = session.createQuery(hql, Long.class);
            setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

            return query.getSingleResult();
        });
    }

    private Optional<String> getOrderByCondition(String sortBy) {
        if (sortBy == null) {
            return Optional.of(" ORDER BY e.created DESC");
        } else {
            Matcher sortByMatcher = SORT_BY_PATTERN.matcher(sortBy);
            if (sortByMatcher.matches()) {
                String sortField = getSortField(sortByMatcher.group(1));
                String sortDirection = sortByMatcher.group(2);
                return Optional.of(" ORDER BY " + sortField + " " + sortDirection + ", e.created DESC");
            } else {
                return Optional.empty();
            }
        }
    }

    private Uni<List<UUID>> getEventIds(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                        LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults,
                                        Integer limit, Integer offset, Optional<String> orderByCondition) {
        return sessionFactory.withSession(session -> {
            String hql = "SELECT e.id FROM Event e JOIN e.eventType et JOIN et.application a JOIN a.bundle b " +
                    "WHERE e.accountId = :accountId";

            hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

            if (orderByCondition.isPresent()) {
                hql += orderByCondition.get();
            }

            Mutiny.Query<UUID> query = session.createQuery(hql, UUID.class);
            setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

            if (limit != null) {
                query.setMaxResults(limit);
            }
            if (offset != null) {
                query.setFirstResult(offset);
            }

            return query.getResultList();
        });
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        if (bundleIds != null && !bundleIds.isEmpty()) {
            hql += " AND b.id IN (:bundleIds)";
        }
        if (appIds != null && !appIds.isEmpty()) {
            hql += " AND a.id IN (:appIds)";
        }
        if (eventTypeDisplayName != null) {
            hql += " AND LOWER(et.displayName) LIKE :eventTypeDisplayName";
        }
        if (startDate != null && endDate != null) {
            hql += " AND DATE(e.created) BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND DATE(e.created) >= :startDate";
        } else if (endDate != null) {
            hql += " AND DATE(e.created) <= :endDate";
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

    private static void setQueryParams(Mutiny.Query<?> query, String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
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
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
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
                return "b.displayName";
            case "application":
                return "a.displayName";
            case "event":
                return "et.displayName";
            case "created":
                return "e.created";
            default:
                throw new IllegalArgumentException("Unknown sort field: " + field);
        }
    }
}
