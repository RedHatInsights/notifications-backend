package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.Map;

public abstract class AbstractEmailPayloadAggregator {

    private static final String START_TIME_KEY = "start_time";
    private static final String END_TIME_KEY = "end_time";

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String orgId;
    private int processedAggregations;

    JsonObject context = new JsonObject();

    abstract void processEmailAggregation(EmailAggregation aggregation);

    public void aggregate(EmailAggregation aggregation) {
        if (orgId == null) {
            orgId = aggregation.getOrgId();
        } else if (!orgId.equals(aggregation.getOrgId())) {
            throw new RuntimeException("Invalid aggregation using different orgIds");
        }

        processEmailAggregation(aggregation);
        ++processedAggregations;
    }

    public Map<String, Object> getContext() {
        Map<String, Object> payload = this.context.mapTo(Map.class);
        payload.put(START_TIME_KEY, this.startTime);
        payload.put(END_TIME_KEY, this.endTime);
        return payload;
    }

    public boolean isEmpty() {
        return false;
    }

    void copyStringField(JsonObject to, JsonObject from, final String field) {
        to.put(field, from.getString(field));
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTimeKey(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    String getOrgId() {
        return orgId;
    }

    public int getProcessedAggregations() {
        return this.processedAggregations;
    }
}
