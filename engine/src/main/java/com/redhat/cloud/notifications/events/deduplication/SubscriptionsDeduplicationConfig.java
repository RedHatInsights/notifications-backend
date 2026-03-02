package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static java.time.temporal.ChronoUnit.DAYS;

@ApplicationScoped
public class SubscriptionsDeduplicationConfig implements EventDeduplicationConfig {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<EndpointType> RECIPIENTS_ENDPOINT_TYPES = List.of(DRAWER, EMAIL_SUBSCRIPTION);

    @Inject
    EngineConfig engineConfig;

    @Inject
    EntityManager entityManager;

    @Override
    public LocalDateTime getDeleteAfter(Event event) {
        // The event_deduplication entries will be purged from the DB on the first day of the next month, when the first purge cronjob runs after midnight UTC.
        return event.getTimestamp().plusMonths(1).withDayOfMonth(1).truncatedTo(DAYS);
    }

    @Override
    public Optional<String> getDeduplicationKey(Event event) {

        JsonObject context = new JsonObject(event.getPayload()).getJsonObject("context");
        if (context == null) {
            Log.debug("Deduplication key could not be built because of a missing context");
            return Optional.empty();
        }

        // TODO We could build a simpler String key with all field values concatenated. Check if the JSON data structure has a significant impact on the query performances.
        // Use TreeMap to ensure consistent alphabetical ordering of fields in the JSON output
        TreeMap<String, Object> deduplicationKey = new TreeMap<>();
        deduplicationKey.put("org_id", event.getOrgId());
        deduplicationKey.put("product_id", context.getString("product_id"));
        deduplicationKey.put("metric_id", context.getString("metric_id"));
        deduplicationKey.put("billing_account_id", context.getString("billing_account_id"));
        deduplicationKey.put("month", event.getTimestamp().format(MONTH_FORMATTER));
        if (engineConfig.isSubscriptionsDeduplicationWillBeNotifiedEnabled(event.getOrgId())) {
            deduplicationKey.put("will_be_notified", willBeNotified(event.getOrgId(), event.getEventType().getId()));
        }

        return Optional.of(new JsonObject(deduplicationKey).encode());
    }

    boolean willBeNotified(String orgId, UUID eventTypeId) {

        /*
         * Check if the org will receive a notification for the given event type.
         * An org will be notified if it has an enabled endpoint linked to the event type and either:
         * - The endpoint is a machine-to-machine integration (not DRAWER or EMAIL_SUBSCRIPTION), OR
         * - The endpoint is a recipient-based integration (DRAWER or EMAIL_SUBSCRIPTION) AND
         *   at least one user in the org has an active subscription for this event type (either via
         *   the subscribed flag or via any severity subscription in the severities JSON column)
         */
        String hql = """
            SELECT COUNT(e) > 0
            FROM Endpoint e JOIN e.eventTypes et
            WHERE et.id = :eventTypeId AND (e.orgId = :orgId OR e.orgId IS NULL) AND e.enabled = true
            AND (
                e.compositeType.type NOT IN :recipientsEndpointTypes
                OR (
                    e.compositeType.type IN :recipientsEndpointTypes
                    AND EXISTS (
                        SELECT 1 FROM EventTypeEmailSubscription es
                        WHERE es.id.orgId = :orgId
                        AND es.id.eventTypeId = :eventTypeId
                        AND (
                            es.subscribed IS true
                            OR CAST(es.severities AS STRING) LIKE '%true%'
                        )
                    )
                )
            )
            """;

        boolean willBeNotified = entityManager.createQuery(hql, Boolean.class)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("orgId", orgId)
                .setParameter("recipientsEndpointTypes", RECIPIENTS_ENDPOINT_TYPES)
                .getSingleResult();

        Log.debugf("Org %s %s be notified for event type %s", orgId, willBeNotified ? "will" : "will NOT", eventTypeId);

        return willBeNotified;
    }
}
