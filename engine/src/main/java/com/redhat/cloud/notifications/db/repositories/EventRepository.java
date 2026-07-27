package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    EngineConfig engineConfig;

    @Transactional
    public Event create(Event event) {
        entityManager.persist(event);
        return event;
    }

    /**
     * Finds the events related to the provided org id in order to export
     * them, returning an {@link Iterator} that fetches one bounded page at a
     * time using keyset ("seek") pagination, instead of loading the whole
     * matching result set into memory at once. Only the page returned by the
     * last call to {@link Iterator#next()} is held in memory; the small
     * "next page of ids" lookahead used internally to answer
     * {@link Iterator#hasNext()} does not carry the display name columns, so
     * it does not duplicate a full page of events in memory. It is the
     * caller's responsibility to provide valid "from" and "to" filters.
     * @param orgId the org id the events are related to.
     * @param from the initial date to filter the dates from.
     * @param to the final date to filter the dates from.
     * @return an iterator over the pages of events that comply with the
     * provided filters, in ascending "created" order.
     */
    public Iterator<List<Event>> findEventsToExport(final String orgId, final LocalDate from, final LocalDate to) {
        final Timestamp createdMin = from == null ? null : Timestamp.valueOf(from.atStartOfDay());
        final Timestamp createdMax = to == null ? null : Timestamp.valueOf(to.atTime(LocalTime.MAX));

        final boolean normalizedQueries = engineConfig.isNormalizedQueriesEnabled(orgId);
        final int pageSize = engineConfig.getEventsExportPageSize();

        if (pageSize <= 0) {
            throw new IllegalStateException("the \"notifications.events.export.page-size\" configuration property must be a positive integer, but was: " + pageSize);
        }

        return new Iterator<>() {
            private List<Object[]> idsPage = findEventIdsPage(orgId, createdMin, createdMax, null, null, pageSize);

            @Override
            public boolean hasNext() {
                return !idsPage.isEmpty();
            }

            @Override
            public List<Event> next() {
                if (idsPage.isEmpty()) {
                    throw new NoSuchElementException();
                }

                final List<UUID> pageIds = idsPage.stream().map(row -> (UUID) row[0]).toList();
                final Object[] lastRow = idsPage.get(idsPage.size() - 1);
                final UUID lastId = (UUID) lastRow[0];
                final Timestamp lastCreated = (Timestamp) lastRow[1];

                final List<Event> events = findEventsByIds(pageIds, normalizedQueries);

                idsPage = idsPage.size() < pageSize
                    ? List.of()
                    : findEventIdsPage(orgId, createdMin, createdMax, lastCreated, lastId, pageSize);

                return events;
            }
        };
    }

    /**
     * Phase 1 of the two-phase export fetch: finds the next page of matching
     * event IDs, ordered by "created" and "id", starting strictly after the
     * given cursor. This is a cheap, index-only scan since it does not
     * select any of the display name columns.
     * @param orgId the org id the events are related to.
     * @param createdMin the minimum "created" timestamp to filter on, or
     *                    {@code null} if there is no lower bound.
     * @param createdMax the maximum "created" timestamp to filter on, or
     *                    {@code null} if there is no upper bound.
     * @param cursorCreated the "created" timestamp of the last event of the
     *                       previous page, or {@code null} for the first page.
     * @param cursorId the id of the last event of the previous page, or
     *                 {@code null} for the first page.
     * @param pageSize the maximum number of ids to fetch.
     * @return a list of {@code [id, created]} pairs.
     */
    private List<Object[]> findEventIdsPage(final String orgId, final Timestamp createdMin, final Timestamp createdMax, final Timestamp cursorCreated, final UUID cursorId, final int pageSize) {
        final StringBuilder findEventIdsQuery = new StringBuilder(
            "SELECT e.id, e.created FROM Event e WHERE e.orgId = :orgId"
        );

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("orgId", orgId);

        if (createdMin != null) {
            findEventIdsQuery.append(" AND e.created >= :createdMin");
            parameters.put("createdMin", createdMin);
        }

        if (createdMax != null) {
            findEventIdsQuery.append(" AND e.created <= :createdMax");
            parameters.put("createdMax", createdMax);
        }

        if (cursorCreated != null) {
            findEventIdsQuery.append(" AND (e.created > :cursorCreated OR (e.created = :cursorCreated AND e.id > :cursorId))");
            parameters.put("cursorCreated", cursorCreated);
            parameters.put("cursorId", cursorId);
        }

        findEventIdsQuery.append(" ORDER BY e.created ASC, e.id ASC");

        final TypedQuery<Object[]> query = entityManager.createQuery(findEventIdsQuery.toString(), Object[].class);

        for (final Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        query.setMaxResults(pageSize);

        return query.getResultList();
    }

    /**
     * Phase 2 of the two-phase export fetch: fetches the full projection
     * (including the display name columns) for a single, already bounded
     * page of event ids.
     * @param ids the ids of the events to fetch, as found by
     *            {@link #findEventIdsPage}.
     * @param normalizedQueries whether the "normalized" event tables should
     *                          be joined against to fetch the display names,
     *                          instead of using the denormalized columns.
     * @return the list of events, in ascending "created" order.
     */
    private List<Event> findEventsByIds(final List<UUID> ids, final boolean normalizedQueries) {
        final String findEventsQuery;

        if (normalizedQueries) {
            findEventsQuery =
                "SELECT NEW com.redhat.cloud.notifications.models.Event( " +
                    "e.id, " +
                    "bundle.displayName, " +
                    "app.displayName, " +
                    "et.displayName, " +
                    "e.created) " +
                "FROM " +
                    "Event AS e " +
                "JOIN e.eventType et " +
                "JOIN et.application app " +
                "JOIN app.bundle bundle " +
                "WHERE " +
                    "e.id IN (:ids) " +
                "ORDER BY e.created ASC, e.id ASC";
        } else {
            findEventsQuery =
                "SELECT NEW com.redhat.cloud.notifications.models.Event( " +
                    "e.id, " +
                    "e.bundleDisplayName, " +
                    "e.applicationDisplayName, " +
                    "e.eventTypeDisplayName, " +
                    "e.created) " +
                "FROM " +
                    "Event AS e " +
                "WHERE " +
                    "e.id IN (:ids) " +
                "ORDER BY e.created ASC, e.id ASC";
        }

        return entityManager
            .createQuery(findEventsQuery, Event.class)
            .setParameter("ids", ids)
            .getResultList();
    }

    @Transactional
    public void updateDrawerNotification(Event event) {
        String hql = "UPDATE Event SET renderedDrawerNotification = :renderedDrawerNotification WHERE id = :id";
        entityManager.createQuery(hql)
                .setParameter("renderedDrawerNotification", event.getRenderedDrawerNotification())
                .setParameter("id", event.getId())
                .executeUpdate();
    }

    @Transactional
    public void updateEventDisplayName(UUID eventId, String eventTypeDisplayName) {
        String hql = "UPDATE Event SET eventTypeDisplayName = :eventDisplayName WHERE id = :id";
        entityManager.createQuery(hql)
            .setParameter("eventDisplayName", eventTypeDisplayName)
            .setParameter("id", eventId)
            .executeUpdate();
    }
}
