package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.exports.transformers.PageConsumer;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
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
import java.util.List;
import java.util.Map;
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
     * them, handing them over to the given {@code pageConsumer} one bounded
     * page at a time using keyset ("seek") pagination, instead of loading
     * the whole matching result set into memory at once. It is the caller's
     * responsibility to provide valid "from" and "to" filters.
     * @param orgId the org id the events are related to.
     * @param from the initial date to filter the dates from.
     * @param to the final date to filter the dates from.
     * @param pageConsumer the callback invoked with each page of events that
     *                      comply with the provided filters, in ascending
     *                      "created" order.
     * @throws TransformationException if the page consumer fails to handle a
     * page.
     */
    public void findEventsToExport(final String orgId, final LocalDate from, final LocalDate to, final PageConsumer<Event> pageConsumer) throws TransformationException {
        final Timestamp createdMin = from == null ? null : Timestamp.valueOf(from.atStartOfDay());
        final Timestamp createdMax = to == null ? null : Timestamp.valueOf(to.atTime(LocalTime.MAX));

        final boolean normalizedQueries = engineConfig.isNormalizedQueriesEnabled(orgId);
        final int pageSize = engineConfig.getEventsExportPageSize();

        Timestamp cursorCreated = null;
        UUID cursorId = null;

        while (true) {
            final List<Object[]> idsPage = this.findEventIdsPage(orgId, createdMin, createdMax, cursorCreated, cursorId, pageSize);

            if (idsPage.isEmpty()) {
                break;
            }

            final List<UUID> pageIds = idsPage.stream().map(row -> (UUID) row[0]).toList();

            pageConsumer.accept(this.findEventsByIds(pageIds, normalizedQueries));

            final Object[] lastRow = idsPage.get(idsPage.size() - 1);
            cursorId = (UUID) lastRow[0];
            cursorCreated = (Timestamp) lastRow[1];

            if (idsPage.size() < pageSize) {
                break;
            }
        }
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
