package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;

public class PoliciesEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String POLICIES_KEY = "policies";
    private static final String HOST_KEY = "hosts";
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

    private HashSet<String> uniqueHosts = new HashSet<>();
    private HashMap<String, HashSet<String>> uniqueHostPerPolicy = new HashMap<>();


    public PoliciesEmailPayloadAggregator() {
        payload.put(POLICIES_KEY, new JsonObject());
    }

    public void processEmailAggregation(EmailAggregation aggregation) {
        JsonObject aggregationPayload = aggregation.getPayload();
        //Todo: Validate payload before processing
        String policyId = aggregationPayload.getString(POLICY_ID);
        JsonObject policies = payload.getJsonObject(POLICIES_KEY);

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

    public Integer getUniqueHostCount() {
        return this.uniqueHosts.size();
    }
}
