package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.UUID;

public abstract class AbstractEmailPayloadAggregator {

    private String orgId;

    public String userName;
    public Map<UUID, Map<Severity, Boolean>> severitiesByEventType;
    JsonObject context = new JsonObject();

    abstract void processEmailAggregation(EmailAggregation aggregation);

    public void aggregate(EmailAggregation aggregation) {
        if (orgId == null) {
            orgId = aggregation.getOrgId();
        } else if (!orgId.equals(aggregation.getOrgId())) {
            throw new RuntimeException("Invalid aggregation using different orgIds");
        }

        boolean shouldAggregateThisEvent = true;
        if (severitiesByEventType != null) {
            Map<Severity, Boolean> severities = severitiesByEventType.get(aggregation.getEventTypeId());
            if (severities != null && severities.containsKey(aggregation.getSeverity())) {
                shouldAggregateThisEvent = severities.get(aggregation.getSeverity());
            } else {
                shouldAggregateThisEvent = false;
            }
        }

        if (shouldAggregateThisEvent) {
            processEmailAggregation(aggregation);
        } else {
            Log.debugf("Event will be skipped for user '%s' because they didn't subscribe to severity %s", userName, aggregation.getSeverity());
        }
    }

    public Map<String, Object> getContext() {
        return context.mapTo(Map.class);
    }

    public boolean isEmpty() {
        return false;
    }

    void copyStringField(JsonObject to, JsonObject from, final String field) {
        to.put(field, from.getString(field));
    }

    String getOrgId() {
        return orgId;
    }
}
