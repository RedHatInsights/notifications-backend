package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EventRepository {
    @Inject
    EntityManager entityManager;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public Event create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getCurrentSession().insert(event);
        return event;
    }

    /**
     * Finds the events related to the provided org id in order to export them.
     * It is the caller's responsibility to provide valid "from" and "to"
     * filters.
     * @param orgId the org id the events are related to.
     * @param from the initial date to filter the dates from.
     * @param to the final date to filter the dates from.
     * @return a list of events that comply with the provided filters.
     */
    public List<Event> findEventsToExport(final String orgId, final LocalDate from, final LocalDate to) {
        this.validateFromTo(from, to);

        final StringBuilder findEventsQuery = new StringBuilder();
        findEventsQuery.append(
            "SELECT NEW com.redhat.cloud.notifications.models.Event( " +
                "e.id, " +
                "e.bundleDisplayName, " +
                "e.applicationDisplayName, " +
                "e.eventTypeDisplayName, " +
                "e.created) " +
            "FROM " +
                "Event AS e " +
            "WHERE " +
                "e.orgId = :orgId"
        );

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("orgId", List.of(orgId));

        if (from != null) {
            findEventsQuery.append(
                " AND " +
                    "e.created >= :createdMin"
            );

            parameters.put(
                "createdMin",
                Timestamp.valueOf(from.atStartOfDay())
            );
        }

        if (to != null) {
            findEventsQuery.append(
                " AND " +
                    "e.created <= :createdMax"
            );

            parameters.put(
                "createdMax",
                Timestamp.valueOf(to.atTime(LocalTime.MAX))
            );
        }

        final TypedQuery<Event> findEventsRanged = this.entityManager.createQuery(findEventsQuery.toString(), Event.class);

        for (final Map.Entry<String, Object> entry : parameters.entrySet()) {
            findEventsRanged.setParameter(entry.getKey(), entry.getValue());
        }

        return findEventsRanged.getResultList();
    }

    /**
     * <p>Validates that the initial and end date of the filters are valid and
     * throws an exception if they are not. The constraints are:
     * <ul>
     *     <li>{@code null} dates are considered valid.</li>
     *     <li>The initial date can't be older than a month.</li>
     *     <li>The initial date can't be in the future.</li>
     *     <li>If both the initial and end date are provided, the initial date
     *     can't be after the end date.</li>
     *     <li>The end date can't be older than a month.</li>
     *     <li>The end date can't be in the future.</li>
     * </ul></p>
     *
     * @param from the initial date of the filter.
     * @param to the end date of the filter.
     */
    protected void validateFromTo(final LocalDate from, final LocalDate to) {
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final LocalDate aMonthAgo = today.minusMonths(1);

        if (from != null) {
            if (from.isAfter(today)) {
                throw new IllegalStateException("can't fetch events from the future!");
            }

            if (aMonthAgo.isAfter(from)) {
                throw new IllegalStateException("events that are older than a month cannot be fetched");
            }

            if (to != null && to.isBefore(from)) {
                throw new IllegalStateException("the 'to' date cannot be lower than the 'from' date");
            }
        }

        if (to != null) {
            if (to.isAfter(today)) {
                throw new IllegalStateException("can't fetch events from the future!");
            }

            if (aMonthAgo.isAfter(to)) {
                throw new IllegalStateException("events that are older than a month cannot be fetched");
            }
        }
    }
}
