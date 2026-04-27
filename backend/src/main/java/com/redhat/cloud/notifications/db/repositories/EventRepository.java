package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.Sort;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.routers.handlers.event.EventAuthorizationCriterion;
import com.redhat.cloud.notifications.utils.RecipientsAuthorizationCriterionExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    public List<EventAuthorizationCriterion> getEventsWithCriterion(String orgId, boolean useNormalized, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                                                    LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                                                    Set<Boolean> invocationResults, Set<NotificationStatus> status) {

        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypeNameNotEmpty = eventTypeDisplayName != null;

        String hql = "FROM Event e ";

        // Add selective JOINs for normalized approach - only join what we need
        if (useNormalized) {
            hql += "JOIN FETCH e.eventType et JOIN FETCH et.application app JOIN FETCH app.bundle bundle ";
        }

        hql += "WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, useNormalized, bundlesNotEmpty, applicationsNotEmpty, eventTypeNameNotEmpty, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, null, Optional.empty(), true);
        // we are looking for events with auth criterion only
        hql += " AND e.hasAuthorizationCriterion is true";

        TypedQuery<Event> typedQuery = entityManager.createQuery(hql, Event.class);
        setQueryParams(typedQuery, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, null, Optional.empty());

        List<Event> eventsWithAuthorizationCriterion = typedQuery.getResultList();

        // Populate denormalized display name fields from joined entities
        if (useNormalized && !eventsWithAuthorizationCriterion.isEmpty()) {
            for (Event event : eventsWithAuthorizationCriterion) {
                event.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
                event.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
                event.setEventTypeDisplayName(event.getEventType().getDisplayName());
            }
        }

        List<EventAuthorizationCriterion> eventAuthorizationCriterion = new ArrayList<>();
        for (Event event : eventsWithAuthorizationCriterion) {
            eventAuthorizationCriterion.add(new EventAuthorizationCriterion(event.getId(), recipientsAuthorizationCriterionExtractor.extract(event)));
        }
        return eventAuthorizationCriterion;
    }

     /**
     * Get drawer events that have authorization criteria.
     * Filters by event type IDs (from subscriptions) and renderedDrawerNotification.
     *
     * @param orgId Organization ID
     * @param eventTypeIds Set of event type UUIDs to filter by (from subscription query)
     * @param startDate Start date filter (optional)
     * @param endDate End date filter (optional)
     * @return List of events with their authorization criteria
     */
    public List<EventAuthorizationCriterion> getDrawerEventsWithCriterion(String orgId, boolean useNormalized, Set<UUID> eventTypeIds,
                                                                           LocalDateTime startDate, LocalDateTime endDate) {
        boolean eventTypeIdsNotEmpty = eventTypeIds != null && !eventTypeIds.isEmpty();

        String hql = "FROM Event e ";

        // Add selective JOINs for normalized approach
        if (useNormalized) {
            hql += "JOIN FETCH e.eventType et JOIN FETCH et.application app JOIN FETCH app.bundle bundle ";
        }

        hql += "WHERE e.orgId = :orgId";

        // Filter by renderedDrawerNotification (drawer-specific)
        hql += " AND e.renderedDrawerNotification IS NOT NULL";

        // Filter by event type IDs (from subscription query)
        if (eventTypeIdsNotEmpty) {
            if (useNormalized) {
                hql += " AND et.id IN (:eventTypeIds)";
            } else {
                hql += " AND e.eventType.id IN (:eventTypeIds)";
            }
        }

        // Date range filtering
        if (startDate != null && endDate != null) {
            hql += " AND e.created BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND e.created >= :startDate";
        } else if (endDate != null) {
            hql += " AND e.created <= :endDate";
        }

        // Drawer only cares about events with auth criterion
        hql += " AND e.hasAuthorizationCriterion is true";

        TypedQuery<Event> typedQuery = entityManager.createQuery(hql, Event.class);
        typedQuery.setParameter("orgId", orgId);

        if (eventTypeIdsNotEmpty) {
            typedQuery.setParameter("eventTypeIds", eventTypeIds);
        }

        if (startDate != null) {
            typedQuery.setParameter("startDate", Timestamp.valueOf(startDate.toLocalDate().atStartOfDay()));
        }
        if (endDate != null) {
            typedQuery.setParameter("endDate", Timestamp.valueOf(endDate.toLocalDate().atTime(LocalTime.MAX)));
        }

        List<Event> eventsWithAuthorizationCriterion = typedQuery.getResultList();

        List<EventAuthorizationCriterion> eventAuthorizationCriterion = new ArrayList<>();
        for (Event event : eventsWithAuthorizationCriterion) {
            eventAuthorizationCriterion.add(new EventAuthorizationCriterion(event.getId(), recipientsAuthorizationCriterionExtractor.extract(event)));
        }
        return eventAuthorizationCriterion;
    }

    public List<Event> getEvents(String orgId, boolean useNormalized, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                      Set<Boolean> invocationResults, boolean fetchNotificationHistory, Set<NotificationStatus> status, Set<Severity> severities, Query query,
                                      Optional<List<UUID>> uuidToExclude, boolean includeEventsWithAuthCriterion) {

        Optional<Sort> sort = Sort.getSort(query, "created:DESC", Event.getSortFields(useNormalized));

        List<UUID> eventIds = getEventIds(orgId, useNormalized, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, severities, query, uuidToExclude, includeEventsWithAuthCriterion);
        if (eventIds.isEmpty()) {
            return new ArrayList<>();
        }

        String hql;
        if (useNormalized) {
            String joinClause = "JOIN FETCH e.eventType et JOIN FETCH et.application app JOIN FETCH app.bundle bundle ";

            if (fetchNotificationHistory) {
                // Remove DISTINCT to allow ORDER BY with joined columns
                hql = "SELECT e FROM Event e " + joinClause + "LEFT JOIN FETCH e.historyEntries he WHERE e.id IN (:eventIds)";
            } else {
                hql = "FROM Event e " + joinClause + "WHERE e.id IN (:eventIds)";
            }

        } else {
            if (fetchNotificationHistory) {
                hql = "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.historyEntries he WHERE e.id IN (:eventIds)";
            } else {
                hql = "FROM Event e WHERE e.id IN (:eventIds)";
            }
        }

        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        List<Event> events = entityManager.createQuery(hql, Event.class)
                .setParameter("eventIds", eventIds)
                .getResultList();

        if (useNormalized && !events.isEmpty()) {
            // LEFT JOIN FETCH on one-to-many can create duplicate Event objects
            // Only deduplicate for normalized queries (denormalized already has SQL DISTINCT)
            if (fetchNotificationHistory) {
                events = events.stream()
                        .distinct()
                        .collect(Collectors.toList());
            }

            // Populate denormalized display name fields from joined entities
            for (Event event : events) {
                event.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
                event.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
                event.setEventTypeDisplayName(event.getEventType().getDisplayName());
            }
        }

        return events;
    }

    public Long count(String orgId, boolean useNormalized, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                      LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                      Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults,
                      Set<NotificationStatus> status, Set<Severity> severities, Optional<List<UUID>> uuidToExclude, Boolean includeEventsWithAuthCriterion) {

        // Calculate once for reuse
        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypeNameNotEmpty = eventTypeDisplayName != null;

        String hql = "SELECT COUNT(*) FROM Event e ";

        // Add selective JOINs for normalized approach - only join what we need
        if (useNormalized && (bundlesNotEmpty || applicationsNotEmpty || eventTypeNameNotEmpty)) {
            hql += "JOIN e.eventType et ";

            if (bundlesNotEmpty || applicationsNotEmpty) {
                hql += "JOIN et.application app ";
            }

            if (bundlesNotEmpty) {
                hql += "JOIN app.bundle bundle ";
            }
        }

        hql += "WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, useNormalized, bundlesNotEmpty, applicationsNotEmpty, eventTypeNameNotEmpty, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, severities, uuidToExclude, includeEventsWithAuthCriterion);

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
        setQueryParams(query, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, severities, uuidToExclude);

        return query.getSingleResult();
    }

    private String getOrderBy(Sort sort) {
        if (!sort.getSortColumn().equals("e.created")) {
            return " " + sort.getSortQuery() + ", e.created DESC";
        } else {
            return " " + sort.getSortQuery();
        }
    }

    private List<UUID> getEventIds(String orgId, boolean useNormalized, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                        LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes, Set<CompositeEndpointType> compositeEndpointTypes,
                                        Set<Boolean> invocationResults, Set<NotificationStatus> status, Set<Severity> severities, Query query, Optional<List<UUID>> uuidToExclude, boolean includeEventsWithAuthCriterion) {
        boolean bundlesNotEmpty = bundleIds != null && !bundleIds.isEmpty();
        boolean applicationsNotEmpty = appIds != null && !appIds.isEmpty();
        boolean eventTypeNameNotEmpty = eventTypeDisplayName != null;
        Optional<Sort> sort = Sort.getSort(query, "created:DESC", Event.getSortFields(useNormalized));

        String hql = "SELECT e.id FROM Event e ";

        if (useNormalized) {
            // Determine which JOINs are needed based on filters and sort
            String sortColumn = sort.isPresent() ? sort.get().getSortColumn() : "";

            boolean needsBundle = bundlesNotEmpty || sortColumn.startsWith("bundle.");
            boolean needsApp = applicationsNotEmpty || needsBundle || sortColumn.startsWith("app.");
            boolean needsEventType = eventTypeNameNotEmpty || needsApp || sortColumn.startsWith("et.");

            // Add selective JOINs (order matters - must follow FK chain: e → et → app → bundle)
            if (needsEventType) {
                hql += "JOIN e.eventType et ";
            }
            if (needsApp) {
                hql += "JOIN et.application app ";
            }
            if (needsBundle) {
                hql += "JOIN app.bundle bundle ";
            }
        }

        hql += "WHERE e.orgId = :orgId";

        hql = addHqlConditions(hql, useNormalized, bundlesNotEmpty, applicationsNotEmpty, eventTypeNameNotEmpty, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, severities, uuidToExclude, includeEventsWithAuthCriterion);

        if (sort.isPresent()) {
            hql += getOrderBy(sort.get());
        }

        TypedQuery<UUID> typedQuery = entityManager.createQuery(hql, UUID.class);
        setQueryParams(typedQuery, orgId, bundleIds, appIds, eventTypeDisplayName, startDate, endDate, endpointTypes, compositeEndpointTypes, invocationResults, status, severities, uuidToExclude);

        Query.Limit limit = query.getLimit();

        typedQuery.setMaxResults(limit.getLimit());
        typedQuery.setFirstResult(limit.getOffset());

        return typedQuery.getResultList();
    }

    private String addHqlConditions(String hql, boolean useNormalized, boolean bundlesNotEmpty, boolean applicationsNotEmpty, boolean eventTypeNameNotEmpty,
                                           LocalDate startDate, LocalDate endDate, Set<EndpointType> endpointTypes,
                                           Set<CompositeEndpointType> compositeEndpointTypes, Set<Boolean> invocationResults,
                                           Set<NotificationStatus> status, Set<Severity> severities, Optional<List<UUID>> uuidToExclude, boolean includeEventsWithAuthCriterion) {

        List<String> bundleOrAppsConditions = new ArrayList<>();

        if (uuidToExclude.isPresent()) {
            bundleOrAppsConditions.add("e.id NOT IN (:uuidToExclude)");
        }
        if (bundlesNotEmpty) {
            if (useNormalized) {
                bundleOrAppsConditions.add("bundle.id IN (:bundleIds)");
            } else {
                bundleOrAppsConditions.add("e.bundleId IN (:bundleIds)");
            }
        }
        if (applicationsNotEmpty) {
            if (useNormalized) {
                bundleOrAppsConditions.add("app.id IN (:appIds)");
            } else {
                bundleOrAppsConditions.add("e.applicationId IN (:appIds)");
            }
        }

        if (bundleOrAppsConditions.size() > 0) {
            hql += " AND (" + String.join(" OR ", bundleOrAppsConditions) + ")";
        }

        if (eventTypeNameNotEmpty) {
            if (useNormalized) {
                hql += " AND LOWER(e.eventType.displayName) LIKE :eventTypeDisplayName";
            } else {
                hql += " AND LOWER(e.eventTypeDisplayName) LIKE :eventTypeDisplayName";
            }
        }

        if (startDate != null && endDate != null) {
            hql += " AND e.created BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            hql += " AND e.created >= :startDate";
        } else if (endDate != null) {
            hql += " AND e.created <= :endDate";
        }

        if (severities != null && !severities.isEmpty()) {
            hql += " AND e.severity IN (:severities)";
        }

        if (!includeEventsWithAuthCriterion) {
            hql += " AND e.hasAuthorizationCriterion is false";
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
                                       Set<Boolean> invocationResults, Set<NotificationStatus> status, Set<Severity> severities, Optional<List<UUID>> uuidToExclude) {
        query.setParameter("orgId", orgId);
        if (uuidToExclude.isPresent()) {
            query.setParameter("uuidToExclude", uuidToExclude.get());
        }
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
        if (severities != null && !severities.isEmpty()) {
            query.setParameter("severities", severities);
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

    @Transactional
    public void cleanupInventoryEvents(int limit) {
        //  where id in (select id from endpoints where endpoint_type_v2 = 'DRAWER' " +
        //            "and org_id is not null and not exists (select 1 from endpoint_event_type where endpoint_id = id) limit :limit)
        String deleteQuery = "delete from event WHERE id in " +
            "(select id from event where application_id = '332d6b96-5e91-439d-8345-452acac9a722' AND created > '2025-06-20:00:00:00' limit :limit)";
        entityManager.createNativeQuery(deleteQuery)
            .setParameter("limit", limit)
            .executeUpdate();
    }
}
