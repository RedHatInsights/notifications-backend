package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.util.stream.Collectors.toList;


@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Get applications with pending aggregation it the current time matches with org aggregation time preference.
     * Results will be limited to last 48h
     */
    public List<AggregationCommand> getApplicationsWithPendingAggregationAccordingOrgPref(LocalDateTime now) {
        LocalDateTime currentTimeTwoDaysAgo = now.minusDays(2);
        String query = "SELECT DISTINCT ev.orgId, ev.bundleId, ev.applicationId, acp.lastRun, bu.name, ap.name FROM Event ev " +
            "join Application ap on ev.applicationId = ap.id join Bundle bu on ev.bundleId = bu.id " +
            "join AggregationOrgConfig acp on ev.orgId = acp.orgId WHERE " +

            // check than a aggregation template exists for the event application
            "EXISTS (SELECT 1 FROM AggregationEmailTemplate WHERE application.id = ev.applicationId and subscriptionType = 'DAILY') " +
            // After prod validation phase, the previous AggregationEmailTemplate check should be remove in favor of:
            // check than at least one user of the org subscribed for daily digest with this event type
            //"EXISTS (SELECT 1 FROM EventTypeEmailSubscription es where ev.orgId = es.id.orgId and es.id.subscriptionType='DAILY' and es.eventType = ev.eventType and es.subscribed is true) " + warning: need an new index on EventTypeEmailSubscription before being enabled

            // check for linked email integration linked to this event type (to honor legacy mechanism)
            "AND EXISTS (SELECT 1 FROM Endpoint ep, EndpointEventType eet where ev.orgId = ep.orgId and ep.compositeType.type = 'EMAIL_SUBSCRIPTION' and eet.eventType = ev.eventType and eet.endpoint = ep) " +
            // filter on new events since the latest run of this org aggregation, and not older than two days
            "AND (ev.created > acp.lastRun OR acp.lastRun is null) AND ev.created > :twoDaysAgo AND ev.created <= :now " +
            // filter on org scheduled execution time
            "AND :nowTime = acp.scheduledExecutionTime";
        Query hqlQuery = entityManager.createQuery(query)
            .setParameter("nowTime", now.toLocalTime())
            .setParameter("now", now)
            .setParameter("twoDaysAgo", currentTimeTwoDaysAgo);

        List<Object[]> records = hqlQuery.getResultList();
        return records.stream()
            .map(emailAggregationRecord -> new AggregationCommand(
                new EventAggregationCriterion(
                    (String) emailAggregationRecord[0],     // Org id
                    (UUID) emailAggregationRecord[1],       // bundle id
                    (UUID) emailAggregationRecord[2],       // application id
                    (String) emailAggregationRecord[4],     // bundle name
                    (String) emailAggregationRecord[5]),    // application name
                computeStartDateTime((LocalDateTime) emailAggregationRecord[3], currentTimeTwoDaysAgo),
                now,
                DAILY
            ))
            .collect(toList());
    }

    // compute aggregation start date, it must not be older than two days ago
    private static LocalDateTime computeStartDateTime(final LocalDateTime startDateTimeFromDb, final LocalDateTime currentTimeTwoDaysAgo) {
        if (startDateTimeFromDb != null
            && startDateTimeFromDb.isBefore(currentTimeTwoDaysAgo)) {
            return currentTimeTwoDaysAgo;
        } else {
            return startDateTimeFromDb;
        }
    }
}
