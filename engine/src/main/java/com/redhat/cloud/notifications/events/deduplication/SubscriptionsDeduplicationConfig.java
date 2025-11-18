package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.TreeMap;

import static java.time.temporal.ChronoUnit.DAYS;

public class SubscriptionsDeduplicationConfig extends EventDeduplicationConfig {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public SubscriptionsDeduplicationConfig(Event event) {
        super(event);
    }

    @Override
    public LocalDateTime getDeleteAfter() {
        // The event_deduplication entries will be purged from the DB on the first day of the next month, when the first purge cronjob runs after midnight UTC.
        return event.getTimestamp().plusMonths(1).withDayOfMonth(1).truncatedTo(DAYS);
    }

    @Override
    public Optional<String> getDeduplicationKey() {

        JsonObject context = new JsonObject(event.getPayload()).getJsonObject("context");
        if (context == null) {
            Log.debug("Deduplication key could not be built because of a missing context");
            return Optional.empty();
        }

        // TODO We could build a simpler String key with all field values concatenated. Check if the JSON data structure has a significant impact on the query performances.
        // Use TreeMap to ensure consistent alphabetical ordering of fields in the JSON output
        TreeMap<String, Object> deduplicationKey = new TreeMap<>();
        deduplicationKey.put("orgId", event.getOrgId());
        deduplicationKey.put("productId", context.getString("productId"));
        deduplicationKey.put("metricId", context.getString("metricId"));
        deduplicationKey.put("billingAccountId", context.getString("billingAccountId"));
        deduplicationKey.put("month", event.getTimestamp().format(MONTH_FORMATTER));

        return Optional.of(new JsonObject(deduplicationKey).encode());
    }
}
