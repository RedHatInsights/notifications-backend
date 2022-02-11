package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.routers.EventService.PATH;
import static com.redhat.cloud.notifications.routers.EventService.SORT_BY_PATTERN;

@ApplicationScoped
public class EventResources {

    private static final Logger LOGGER = Logger.getLogger(EventResources.class);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Page<EventLogEntry>> getEvents(String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                              LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults,
                                              Integer limit, Integer offset, String sortBy, boolean includeDetails, boolean includePayload) {
        LOGGER.debug("getEvents invoked");
        return sessionFactory.withSession(session -> {
            return getEventIdsAndCount(session, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults, limit, offset, sortBy)
                    .invoke(() -> LOGGER.debug("getEventIdsAndCount ended"))
                    .onItem().transformToUni(records -> {
                        if (records.isEmpty()) {
                            return Uni.createFrom().item(() -> buildPage(Collections.emptyList(), 0L, limit, offset));
                        } else {
                            // The second field of each record contains the total count of events.
                            long count = ((BigInteger) records.get(0)[1]).longValue();
                            List<UUID> eventIds = parseEventIds(records);

                            String hql = "SELECT DISTINCT e FROM Event e " +
                                    "JOIN FETCH e.eventType et JOIN FETCH et.application a JOIN FETCH a.bundle b " +
                                    "LEFT JOIN FETCH e.historyEntries he " +
                                    "WHERE e.id IN (:eventIds)";

                            Optional<String> orderByCondition = getOrderByCondition(sortBy, false);
                            if (orderByCondition.isPresent()) {
                                hql += orderByCondition.get();
                            }

                            LOGGER.debug("Preparing the events retrieval query");
                            return session.createQuery(hql, Event.class)
                                    .setParameter("eventIds", eventIds)
                                    .getResultList()
                                    .onItem().transform(events -> {
                                        LOGGER.debug("Events retrieved from DB, transforming them...");

                                        List<EventLogEntry> entries = events.stream().map(event -> {
                                            List<EventLogEntryAction> actions = event.getHistoryEntries().stream().map(historyEntry -> {
                                                return buildEventLogEntryAction(
                                                        historyEntry.getId(),
                                                        historyEntry.getEndpointId(),
                                                        historyEntry.getEndpointType(),
                                                        historyEntry.getEndpointSubType(),
                                                        historyEntry.isInvocationResult(),
                                                        includeDetails ? historyEntry.getDetails() : null
                                                );
                                            }).collect(Collectors.toList());
                                            return buildEventLogEntry(
                                                    event.getId(),
                                                    event.getCreated(),
                                                    event.getEventType().getApplication().getBundle().getDisplayName(),
                                                    event.getEventType().getApplication().getDisplayName(),
                                                    event.getEventType().getDisplayName(),
                                                    includePayload ? event.getPayload() : null,
                                                    actions
                                            );
                                        }).collect(Collectors.toList());

                                        LOGGER.debug("Events transformed");

                                        return buildPage(entries, count, limit, offset);
                                    });
                        }
                    });
        });
    }

    private Optional<String> getOrderByCondition(String sortBy, boolean nativeQuery) {
        if (sortBy == null) {
            return Optional.of(" ORDER BY e.created DESC");
        } else {
            Matcher sortByMatcher = SORT_BY_PATTERN.matcher(sortBy);
            if (sortByMatcher.matches()) {
                String sortField = getSortField(sortByMatcher.group(1), nativeQuery);
                String sortDirection = sortByMatcher.group(2);
                String result = " ORDER BY " + sortField + " " + sortDirection;
                if (!sortField.equals("e.created")) {
                    result += ", e.created DESC";
                }
                return Optional.of(result);
            } else {
                return Optional.empty();
            }
        }
    }

    private Uni<List<Object[]>> getEventIdsAndCount(Mutiny.Session session, String accountId, Set<UUID> bundleIds, Set<UUID> appIds,
                                                    String eventTypeDisplayName, LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                                                    Set<Boolean> invocationResults, Integer limit, Integer offset, String sortBy) {

        String sql = "SELECT CAST(e.id AS VARCHAR), COUNT(*) OVER() FROM event e, event_type et, applications a, bundles b " +
                "WHERE e.account_id = :accountId AND e.event_type_id = et.id AND et.application_id = a.id AND a.bundle_id = b.id";

        sql = addSqlConditions(sql, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        Optional<String> orderByCondition = getOrderByCondition(sortBy, true);
        if (orderByCondition.isPresent()) {
            sql += orderByCondition.get();
        }

        Mutiny.Query<Object[]> query = session.createNativeQuery(sql);
        setQueryParams(query, accountId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, invocationResults);

        if (limit != null) {
            query.setMaxResults(limit);
        }
        if (offset != null) {
            query.setFirstResult(offset);
        }

        LOGGER.debug("getEventIdsAndCount query is ready to be executed");

        return query.getResultList();
    }

    private static String addSqlConditions(String sql, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        if (bundleIds != null && !bundleIds.isEmpty()) {
            sql += " AND b.id IN (:bundleIds)";
        }
        if (appIds != null && !appIds.isEmpty()) {
            sql += " AND a.id IN (:appIds)";
        }
        if (eventTypeDisplayName != null) {
            sql += " AND LOWER(et.display_name) LIKE :eventTypeDisplayName";
        }
        if (startDate != null && endDate != null) {
            sql += " AND DATE(e.created) BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            sql += " AND DATE(e.created) >= :startDate";
        } else if (endDate != null) {
            sql += " AND DATE(e.created) <= :endDate";
        }

        boolean checkEndpointType = endpointTypes != null && !endpointTypes.isEmpty();
        boolean checkInvocationResult = invocationResults != null && !invocationResults.isEmpty();
        if (checkEndpointType || checkInvocationResult) {
            List<String> subQueryConditions = new ArrayList<>();
            if (checkEndpointType) {
                subQueryConditions.add("nh.endpoint_type IN (:endpointTypes)");
            }
            if (checkInvocationResult) {
                subQueryConditions.add("nh.invocation_result IN (:invocationResults)");
            }
            sql += " AND EXISTS (SELECT 1 FROM notification_history nh WHERE nh.event_id = e.id AND " + String.join(" AND ", subQueryConditions) + ")";
        }
        return sql;
    }

    private static void setQueryParams(Mutiny.Query<?> query, String accountId, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                       LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<Boolean> invocationResults) {
        query.setParameter("accountId", accountId);
        if (bundleIds != null && !bundleIds.isEmpty()) {
            query.setParameter("bundleIds", bundleIds);
        }
        if (appIds != null && !appIds.isEmpty()) {
            query.setParameter("appIds", appIds);
        }
        if (eventTypeDisplayName != null) {
            query.setParameter("eventTypeDisplayName", "%" + eventTypeDisplayName.toLowerCase() + "%");
        }
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        if (endpointTypes != null && !endpointTypes.isEmpty()) {
            query.setParameter("endpointTypes", endpointTypes.stream().map(EndpointType::ordinal).collect(Collectors.toSet()));
        }
        if (invocationResults != null && !invocationResults.isEmpty()) {
            query.setParameter("invocationResults", invocationResults);
        }
    }

    private static String getSortField(String field, boolean nativeQuery) {
        switch (field) {
            case "bundle":
                return nativeQuery ? "b.display_name" : "b.displayName";
            case "application":
                return nativeQuery ? "a.display_name" : "a.displayName";
            case "event":
                return nativeQuery ? "et.display_name" : "et.displayName";
            case "created":
                return "e.created";
            default:
                throw new IllegalArgumentException("Unknown sort field: " + field);
        }
    }

    /*
     * Each entry of the 'records' arg contains two values:
     * - records[0]: an eventId
     * - records[1]: the total count of events (that value is identical for all records)
     */
    private static List<UUID> parseEventIds(List<Object[]> records) {
        List<UUID> result = new ArrayList<>();
        for (Object[] record : records) {
            result.add(UUID.fromString((String) record[0]));
        }
        return result;
    }

    private static EventLogEntry buildEventLogEntry(UUID id, LocalDateTime created, String bundle, String app, String eventType, String payload, List<EventLogEntryAction> actions) {
        EventLogEntry entry = new EventLogEntry();
        entry.setId(id);
        entry.setCreated(created);
        entry.setBundle(bundle);
        entry.setApplication(app);
        entry.setEventType(eventType);
        entry.setActions(actions);
        entry.setPayload(payload);
        return entry;
    }

    private static EventLogEntryAction buildEventLogEntryAction(UUID id, UUID endpointId, EndpointType endpointType, String endpointSubType, Boolean invocationResult, Map<String, Object> details) {
        EventLogEntryAction action = new EventLogEntryAction();
        action.setId(id);
        action.setEndpointId(endpointId);
        action.setEndpointType(endpointType);
        action.setEndpointSubType(endpointSubType);
        action.setInvocationResult(invocationResult);
        action.setDetails(details);
        return action;
    }

    private static Page<EventLogEntry> buildPage(List<EventLogEntry> events, long count, long limit, long offset) {
        Meta meta = new Meta();
        meta.setCount(count);

        Map<String, String> links = PageLinksBuilder.build(PATH, count, limit, offset);

        Page<EventLogEntry> page = new Page<>();
        page.setData(events);
        page.setMeta(meta);
        page.setLinks(links);
        LOGGER.debug("Page is ready to be returned");
        return page;
    }
}
