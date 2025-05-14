package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.util.stream.Collectors.toList;


@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    EntityManager entityManager;

    public List<AggregationCommand> getApplicationsWithPendingAggregationAccordinfOrgPref(LocalDateTime now) {
        LocalDateTime currentTimeTwoDaysAgo = now.minusDays(2);
        // Must takes every EmailAggregation supposed to be processed on last 15 min
        // it covers cases when aggregation job may be run with few minutes late (ie: 05:01, 07,32)
        String query = "SELECT DISTINCT ea.orgId, ea.bundleName, ea.applicationName, acp.lastRun FROM EmailAggregation ea, AggregationOrgConfig acp WHERE " +
            "ea.orgId = acp.orgId AND ea.created > acp.lastRun AND ea.created <= :now " +
            "AND :nowTime >= acp.scheduledExecutionTime AND (:nowTime - acp.scheduledExecutionTime) < :cutoff";
        Query hqlQuery = entityManager.createQuery(query)
                .setParameter("nowTime", now.toLocalTime())
                .setParameter("cutoff", Duration.ofMinutes(15))
                .setParameter("now", now);

        List<Object[]> records = hqlQuery.getResultList();
        return records.stream()
                .map(emailAggregationRecord -> new AggregationCommand<>(
                    new EmailAggregationKey((String) emailAggregationRecord[0], (String) emailAggregationRecord[1], (String) emailAggregationRecord[2]),
                    ((LocalDateTime) emailAggregationRecord[3]).isBefore(currentTimeTwoDaysAgo) ? currentTimeTwoDaysAgo : ((LocalDateTime) emailAggregationRecord[3]),
                    now,
                    DAILY
                ))
                .collect(toList());
    }

    /**
     * Get applications with pending aggregation it the current time matches with org aggregation time preference.
     * Results will be limited to last 48h
     */
    public List<AggregationCommand> getApplicationsWithPendingAggregationAccordingOrgPref(LocalDateTime now) {
        LocalDateTime currentTimeTwoDaysAgo = now.minusDays(2);
        String query = "SELECT DISTINCT ev.orgId, ev.bundleId, ev.applicationId, acp.lastRun, bu.name, ap.name, ev.accountId FROM Event ev " +
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
            "AND ev.created > acp.lastRun AND ev.created > :twoDaysAgo AND ev.created <= :now " +
            // filter on org scheduled execution time
            "AND :nowTime = acp.scheduledExecutionTime";
        Query hqlQuery = entityManager.createQuery(query)
            .setParameter("nowTime", now.toLocalTime())
            .setParameter("now", now)
            .setParameter("twoDaysAgo", currentTimeTwoDaysAgo);

        List<Object[]> records = hqlQuery.getResultList();
        return records.stream()
            .map(emailAggregationRecord -> new AggregationCommand<>(
                new EventAggregationCriteria(
                    (String) emailAggregationRecord[0], // orgId
                    (UUID) emailAggregationRecord[1],   // bundleId
                    (UUID) emailAggregationRecord[2],   // applicationId
                    (String) emailAggregationRecord[4], // bundle name
                    (String) emailAggregationRecord[5], // application name
                    (String) emailAggregationRecord[6]), // accountId
                ((LocalDateTime) emailAggregationRecord[3]).isBefore(currentTimeTwoDaysAgo) ? currentTimeTwoDaysAgo : ((LocalDateTime) emailAggregationRecord[3]),
                now,
                DAILY
            ))
            .collect(toList());
    }
}
