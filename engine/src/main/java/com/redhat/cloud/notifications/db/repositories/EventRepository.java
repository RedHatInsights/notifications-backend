package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Event;
import org.hibernate.Session;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EventRepository {
    /**
     * The maximum hours value. Taken from
     * {@link java.time.temporal.ChronoField#HOUR_OF_DAY}
     */
    private static final int MAX_HOURS = 23;
    /**
     * The maximum minutes value. Taken from
     * {@link java.time.temporal.ChronoField#MINUTE_OF_HOUR}
     */
    private static final int MAX_MINUTES = 59;
    /**
     * The maximum seconds value. Taken from
     * {@link java.time.temporal.ChronoField#SECOND_OF_MINUTE}
     */
    private static final int MAX_SECONDS = 59;
    /**
     * The maximum nanoseconds value. Taken from
     * {@link java.time.temporal.ChronoField#NANO_OF_SECOND}
     */
    private static final int MAX_NANOSECONDS = 999999999;

    @Inject
    EntityManager entityManager;

    @Inject
    Session session;

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

        final CriteriaBuilder criteriaBuilder = this.session.getCriteriaBuilder();
        final CriteriaQuery<Event> criteria = criteriaBuilder.createQuery(Event.class);
        final Root<Event> root = criteria.from(Event.class);

        criteria.multiselect(
            root.get("id"),
            root.get("bundleDisplayName"),
            root.get("applicationDisplayName"),
            root.get("eventTypeDisplayName"),
            root.get("created")
        );

        final List<Predicate> predicates = new ArrayList<>(3);

        // Make sure we are grabbing the events from the correct tenant.
        predicates.add(
            criteriaBuilder.equal(root.get("orgId"), orgId)
        );

        if (from != null) {
            predicates.add(
                criteriaBuilder.greaterThanOrEqualTo(
                    root.get("created"),
                    Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant())
                )
            );
        }

        if (to != null) {
            predicates.add(
                criteriaBuilder.lessThanOrEqualTo(
                    root.get("created"),
                    Date.from(to.atTime(MAX_HOURS, MAX_MINUTES, MAX_SECONDS, MAX_NANOSECONDS).toInstant(ZoneOffset.UTC))
                )
            );
        }

        criteria.where(predicates.toArray(new Predicate[]{}));

        return this.entityManager.createQuery(criteria).getResultList();
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
        final LocalDate today = LocalDate.now();
        final LocalDate aMonthAgo = today.minusMonths(1);

        if (from != null) {
            if (from.compareTo(today) > 0) {
                throw new IllegalStateException("can't fetch events from the future!");
            }

            if (aMonthAgo.compareTo(from) > 0) {
                throw new IllegalStateException("events that are older than a month cannot be fetched");
            }

            if (to != null && to.compareTo(from) < 0) {
                throw new IllegalStateException("the 'to' date cannot be lower than the 'from' date");
            }
        }

        if (to != null) {
            if (to.compareTo(today) > 0) {
                throw new IllegalStateException("can't fetch events from the future!");
            }

            if (aMonthAgo.compareTo(to) > 0) {
                throw new IllegalStateException("events that are older than a month cannot be fetched");
            }
        }
    }
}
