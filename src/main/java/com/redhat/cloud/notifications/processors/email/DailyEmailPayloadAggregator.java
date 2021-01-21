package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class DailyEmailPayloadAggregator {

    private static final String POLICIES_KEY = "policies";
    private static final String HOST_KEY = "hosts";
    private static final String START_TIME_KEY = "start_time";
    private static final String END_TIME_KEY = "end_time";
    private static final String UNIQUE_SYSTEM_COUNT = "unique_system_count";

    // Policy related
    private static final String POLICY_ID = "policy_id";
    private static final String POLICY_NAME = "policy_name";
    private static final String POLICY_DESCRIPTION = "policy_description";
    private static final String POLICY_CONDITION = "policy_condition";

    // Host
    private static final String DISPLAY_NAME = "display_name";
    private static final String INSIGHTS_ID = "insights_id";
    private static final String TAGS = "tags";

    private JsonObject payload = new JsonObject();
    private HashSet<String> uniqueHosts = new HashSet<>();
    private HashMap<String, HashSet<String>> uniqueHostPerPolicy = new HashMap<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String accountId;

    DailyEmailPayloadAggregator() {
        payload.put(POLICIES_KEY, new JsonObject());
    }

    void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    void setEndTimeKey(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    void aggregate(EmailAggregation aggregation) {
        JsonObject aggregationPayload = aggregation.getPayload();
        String policyId = aggregationPayload.getString(POLICY_ID);
        JsonObject policies = payload.getJsonObject(POLICIES_KEY);

        if (accountId == null) {
            accountId = aggregation.getAccountId();
        } else if (!accountId.equals(aggregation.getAccountId())) {
            throw new RuntimeException("Invalid aggregation using different accountIds");
        }

        if (!policies.containsKey(policyId)) {
            JsonObject newPolicy = new JsonObject();
            this.copyStringField(newPolicy, aggregationPayload, POLICY_NAME);
            this.copyStringField(newPolicy, aggregationPayload, POLICY_ID);
            this.copyStringField(newPolicy, aggregationPayload, POLICY_DESCRIPTION);
            this.copyStringField(newPolicy, aggregationPayload, POLICY_CONDITION);

            newPolicy.put(HOST_KEY, new JsonArray());

            policies.put(policyId, newPolicy);
            uniqueHostPerPolicy.put(policyId, new HashSet<>());
        }

        JsonObject policy = policies.getJsonObject(policyId);

        JsonObject host = new JsonObject();
        this.copyStringField(host, aggregationPayload, DISPLAY_NAME);
        this.copyStringField(host, aggregationPayload, INSIGHTS_ID);
        host.put(TAGS, aggregationPayload.getJsonArray(TAGS));
        policy.getJsonArray(HOST_KEY).add(host);

        String insightsId = host.getString(INSIGHTS_ID);
        uniqueHosts.add(insightsId);
        uniqueHostPerPolicy.get(policyId).add(insightsId);

        policy.put(UNIQUE_SYSTEM_COUNT, this.uniqueHostPerPolicy.get(policyId).size());
        this.payload.put(UNIQUE_SYSTEM_COUNT, this.uniqueHosts.size());
    }

    Integer getUniqueHostCount() {
        return this.uniqueHosts.size();
    }

    private void copyStringField(JsonObject to, JsonObject from, final String field) {
        to.put(field, from.getString(field));
    }

    Map<String, Object> getPayload() {
        Map<String, Object> payload = this.payload.mapTo(Map.class);
        payload.put(START_TIME_KEY, this.startTime);
        payload.put(END_TIME_KEY, this.endTime);

        return payload;
    }

    public String getAccountId() {
        return accountId;
    }
}
