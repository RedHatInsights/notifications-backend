package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * Folds every event in the aggregation window into one shared context, regardless of recipient.
 * Unlike engine's per-recipient equivalent, this never filters events out during the fold -- each
 * included item is tagged with metadata instead (see {@link #tagMetadata(JsonObject, EmailAggregation)}),
 * so engine can trim the already-folded result per recipient preference at dispatch time.
 */
public abstract class AbstractEmailPayloadAggregator {

    static final String SEVERITY_KEY = "_severity";
    static final String EVENT_TYPE_KEY = "_eventType";
    static final String SUBSCRIBED_BY_DEFAULT_KEY = "_subscribedByDefault";
    static final String RECIPIENTS_OVERRIDE_KEY = "_recipientsOverride";

    private String orgId;

    JsonObject context = new JsonObject();

    abstract void processEmailAggregation(EmailAggregation aggregation);

    public void aggregate(EmailAggregation aggregation) {
        if (orgId == null) {
            orgId = aggregation.getOrgId();
        } else if (!orgId.equals(aggregation.getOrgId())) {
            throw new RuntimeException("Invalid aggregation using different orgIds");
        }

        processEmailAggregation(aggregation);
    }

    public Map<String, Object> getContext() {
        return context.mapTo(Map.class);
    }

    public boolean isEmpty() {
        return false;
    }

    /**
     * Tags a folded item with everything engine's trim step needs to decide per-recipient inclusion
     * without re-scanning raw events: severity, the originating eventType name (subscriptions are keyed
     * by eventType, not app), whether that eventType is subscribed-to by default (its subscriber set
     * can't be fully enumerated locally), and any raw per-event {@code recipients} override, forwarded
     * verbatim (not parsed/resolved here).
     */
    void tagMetadata(JsonObject item, EmailAggregation aggregation) {
        // Event.severity is a nullable DB column (see DrawerProcessor's equivalent null check); omit
        // the tag rather than NPE on .name(), the item itself is still aggregated.
        if (aggregation.getSeverity() != null) {
            item.put(SEVERITY_KEY, aggregation.getSeverity().name());
        } else {
            Log.warnf("Event has no severity set, skipping severity tag for orgId=%s", getOrgId());
        }
        item.put(EVENT_TYPE_KEY, aggregation.getEventTypeName());
        item.put(SUBSCRIBED_BY_DEFAULT_KEY, aggregation.isSubscribedByDefault());
        if (aggregation.getRecipientsOverride() != null) {
            item.put(RECIPIENTS_OVERRIDE_KEY, aggregation.getRecipientsOverride());
        }
    }

    String getOrgId() {
        return orgId;
    }
}
