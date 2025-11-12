package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static java.time.temporal.ChronoUnit.DAYS;

public class SubscriptionsDeduplicationConfig extends EventDeduplicationConfig {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public SubscriptionsDeduplicationConfig(Event event) {
        super(event);
    }

    @Override
    public LocalDateTime getDeleteAfter() {
        // The event_deduplication entries will be purged from the DB on the first day of the next month, when the first purge cronjob runs after midnight UTC.
        return getEventTimestamp().plusMonths(1).withDayOfMonth(1).truncatedTo(DAYS);
    }

    @Override
    public String getDeduplicationKey() {

        JsonObject eventPayload = new JsonObject(event.getPayload());

        // TODO We could build a simpler String key with all field values concatenated. Check if the JsonObject has a significant impact on the query performances.
        JsonObject deduplicationKey = new JsonObject();
        deduplicationKey.put("orgId", eventPayload.getString("orgId"));
        deduplicationKey.put("productId", eventPayload.getString("productId"));
        deduplicationKey.put("metricId", eventPayload.getString("metricId"));
        deduplicationKey.put("billingAccountId", eventPayload.getString("billingAccountId"));
        deduplicationKey.put("month", getEventTimestamp().format(MONTH_FORMATTER));

        return deduplicationKey.encode();
    }
}
