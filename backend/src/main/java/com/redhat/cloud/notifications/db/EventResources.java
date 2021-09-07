package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Event;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import static com.redhat.cloud.notifications.routers.EventService.SORT_BY_PATTERN;

@ApplicationScoped
public class EventResources {

    @Inject
    Mutiny.Session session;

    @Inject
    Mutiny.StatelessSession statelessSession;

    // Note: This method uses a stateless session
    public Uni<Event> create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        return statelessSession.insert(event)
                .replaceWith(event);
    }

    public Uni<List<Event>> get(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                LocalDate startDate, LocalDate endDate, Integer limit, Integer offset, String sortBy) {
        String hql = "SELECT DISTINCT e FROM Event e " +
                "JOIN FETCH e.eventType et JOIN FETCH et.application a JOIN FETCH a.bundle b " +
                "LEFT JOIN FETCH e.historyEntries he LEFT JOIN FETCH he.endpoint " +
                "WHERE e.accountId = :accountId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate);

        if (sortBy == null) {
            hql += " ORDER BY e.created DESC";
        } else {
            Matcher sortByMatcher = SORT_BY_PATTERN.matcher(sortBy);
            if (sortByMatcher.matches()) {
                String sortField = getSortField(sortByMatcher.group(1));
                String sortDirection = sortByMatcher.group(2);
                hql += " ORDER BY " + sortField + " " + sortDirection + ", e.created DESC";
            }
        }

        Mutiny.Query<Event> query = session.createQuery(hql, Event.class);
        setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate);

        if (limit != null) {
            query.setMaxResults(limit);
        }
        if (offset != null) {
            query.setFirstResult(offset);
        }

        return query.getResultList();
    }

    public Uni<Long> count(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate) {
        String hql = "SELECT COUNT(*) FROM Event e JOIN e.eventType et JOIN et.application a JOIN a.bundle b " +
                "WHERE e.accountId = :accountId";

        hql = addHqlConditions(hql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate);

        Mutiny.Query<Long> query = session.createQuery(hql, Long.class);
        setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate);

        return query.getSingleResult();
    }

    private static String addHqlConditions(String hql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate) {
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
        return hql;
    }

    private static void setQueryParams(Mutiny.Query<?> query, String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
                                       LocalDate startDate, LocalDate endDate) {
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
