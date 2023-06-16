package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EventRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Event create(Event event) {
        entityManager.persist(event);
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
        parameters.put("orgId", orgId);

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

        final TypedQuery<Event> findEventsRanged = entityManager
            .createQuery(findEventsQuery.toString(), Event.class);

        for (final Map.Entry<String, Object> entry : parameters.entrySet()) {
            findEventsRanged.setParameter(entry.getKey(), entry.getValue());
        }

        return findEventsRanged.getResultList();
    }

    @Transactional
    public void updateDrawerNotification(Event event) {
        String hql = "UPDATE Event SET renderedDrawerNotification = :renderedDrawerNotification WHERE id = :id";
        entityManager.createQuery(hql)
                .setParameter("renderedDrawerNotification", event.getRenderedDrawerNotification())
                .setParameter("id", event.getId())
                .executeUpdate();
    }
}
