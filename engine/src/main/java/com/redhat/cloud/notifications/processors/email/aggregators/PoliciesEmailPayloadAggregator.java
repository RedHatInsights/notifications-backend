package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class PoliciesEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String POLICIES_KEY = "policies";
    private static final String HOST_KEY = "hosts";
    private static final String UNIQUE_SYSTEM_COUNT = "unique_system_count";
    private static final String CONTEXT_KEY = "context";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    // Policy related
    private static final String POLICY_ID = "policy_id";
    private static final String POLICY_NAME = "policy_name";
    private static final String POLICY_DESCRIPTION = "policy_description";
    private static final String POLICY_CONDITION = "policy_condition";

    // Host
    private static final String DISPLAY_NAME = "display_name";
    private static final String INVENTORY_ID = "inventory_id";
    private static final String TAGS = "tags";

    private final Set<String> uniqueHosts = new HashSet<>();
    private final Map<String, HashSet<String>> uniqueHostPerPolicy = new HashMap<>();
    private final Map<String, JsonObject> policies = new HashMap<>();

    PoliciesEmailPayloadAggregator() {
    }

    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notificationJson = notification.getPayload();

        JsonObject notifContext = notificationJson.getJsonObject(CONTEXT_KEY);

        JsonObject host = new JsonObject();
        this.copyStringField(host, notifContext, DISPLAY_NAME);
        this.copyStringField(host, notifContext, INVENTORY_ID);
        host.put(TAGS, notifContext.getJsonArray(TAGS));

        notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String policyId = payload.getString(POLICY_ID);

            if (!policies.containsKey(policyId)) {
                JsonObject newPolicy = new JsonObject();
                this.copyStringField(newPolicy, payload, POLICY_NAME);
                this.copyStringField(newPolicy, payload, POLICY_ID);
                this.copyStringField(newPolicy, payload, POLICY_DESCRIPTION);
                this.copyStringField(newPolicy, payload, POLICY_CONDITION);

                newPolicy.put(HOST_KEY, new JsonArray());

                policies.put(policyId, newPolicy);
                uniqueHostPerPolicy.put(policyId, new HashSet<>());
            }

            JsonObject policy = policies.get(policyId);
            String insightsId = host.getString(INVENTORY_ID);

            // Only add host if it hasn't been added to this policy yet (deduplication)
            if (!uniqueHostPerPolicy.get(policyId).contains(insightsId)) {
                policy.getJsonArray(HOST_KEY).add(host);
                uniqueHostPerPolicy.get(policyId).add(insightsId);
            }
        });

        String insightsId = host.getString(INVENTORY_ID);
        uniqueHosts.add(insightsId);
    }

    Integer getUniqueHostCount() {
        return this.uniqueHosts.size();
    }

    @Override
    public Map<String, Object> getContext() {
        // Populate the context with the final aggregated data
        JsonObject policiesJson = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : policies.entrySet()) {
            JsonObject policy = entry.getValue();
            String policyId = entry.getKey();
            policy.put(UNIQUE_SYSTEM_COUNT, uniqueHostPerPolicy.get(policyId).size());
            policiesJson.put(policyId, policy);
        }
        context.put(POLICIES_KEY, policiesJson);
        context.put(UNIQUE_SYSTEM_COUNT, uniqueHosts.size());

        return super.getContext();
    }
}
