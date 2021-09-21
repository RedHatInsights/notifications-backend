package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DriftEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String DRIFT_KEY = "drift";
    private static final String HOST_KEY = "hosts";
    private static final String UNIQUE_SYSTEM_COUNT = "unique_system_count";
    private static final String CONTEXT_KEY = "context";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    // Drift Payload
    private static final String BASELINE_ID = "baseline_id";
    private static final String BASELINE_NAME = "baseline_name";

    private static final String DISPLAY_NAME = "display_name";
    private static final String INVENTORY_ID = "inventory_id";
    private static final String TAGS = "tags";

    private final Set<String> uniqueHosts = new HashSet<>();
    private final Map<String, HashSet<String>> uniqueHostPerBaseline = new HashMap<>();

    public DriftEmailPayloadAggregator() {
        context.put(DRIFT_KEY, new JsonObject());
    }

    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notificationJson = notification.getPayload();
        JsonObject drift = context.getJsonObject(DRIFT_KEY);
        JsonObject context = notificationJson.getJsonObject(CONTEXT_KEY);

        JsonObject host = new JsonObject();
        this.copyStringField(host, context, DISPLAY_NAME);
        this.copyStringField(host, context, INVENTORY_ID);
        host.put(TAGS, context.getJsonArray(TAGS));

        notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String baselineId = payload.getString(BASELINE_ID);

            if (!drift.containsKey(baselineId)) {
                JsonObject newBaseline = new JsonObject();
                this.copyStringField(newBaseline, payload, BASELINE_ID);
                this.copyStringField(newBaseline, payload, BASELINE_NAME);

                newBaseline.put(HOST_KEY, new JsonArray());

                drift.put(baselineId, newBaseline);
                uniqueHostPerBaseline.put(baselineId, new HashSet<>());
            }
            String insightsId = host.getString(INVENTORY_ID);

            if (!hasSystem(drift, baselineId, insightsId)) {
                JsonObject baseline = drift.getJsonObject(baselineId);
                baseline.getJsonArray(HOST_KEY).add(host);
                uniqueHostPerBaseline.get(baselineId).add(insightsId);
                baseline.put(UNIQUE_SYSTEM_COUNT, this.uniqueHostPerBaseline.get(baselineId).size());
            }

        });

        String insightsId = host.getString(INVENTORY_ID);
        uniqueHosts.add(insightsId);
        this.context.put(UNIQUE_SYSTEM_COUNT, this.uniqueHosts.size());
    }

    public Integer getUniqueHostCount() {
        return this.uniqueHosts.size();
    }

    public Boolean hasSystem(JsonObject drift, String baselineId, String insightsId) {
        JsonObject baseline = drift.getJsonObject(baselineId);
        JsonArray systems = baseline.getJsonArray(HOST_KEY);
        for (int i = 0; i < systems.size(); i++) {
            if (systems.getJsonObject(i).getString(INVENTORY_ID).equals(insightsId)) {
                return true;
            }
        }
        return false;
    }

}
