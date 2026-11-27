package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class ResourceOptimizationPayloadAggregator extends AbstractEmailPayloadAggregator {

    public static final String AGGREGATED_DATA = "aggregated_data";
    public static final String STATES = "states";
    public static final String SYSTEMS_TRIGGERED = "systems_triggered";
    public static final String SYSTEMS_WITH_SUGGESTIONS = "systems_with_suggestions";
    public static final String STATE = "state";
    public static final String SYSTEM_COUNT = "system_count";

    private final Map</* inventory_id */ String, /* current_state */ String> currentStates = new HashMap<>();

    ResourceOptimizationPayloadAggregator() {
        context.put(AGGREGATED_DATA, new JsonObject());
    }

    /*
     * When the aggregation process starts (once a day), an instance of
     * ResourceOptimizationEmailAggregator is created. That instance will process
     * all email aggregations records found in the DB for a specific tenant
     * (identified by its org_id). This method will be invoked once per email
     * aggregation record to process. The aggregated data is stored at class level
     * which means each time this method is invoked, it modifies the data that
     * resulted from the preceding invocation. After the last invocation of this
     * method, the final aggregated data is used in the daily email template.
     */
    void processEmailAggregation(EmailAggregation aggregation) {
        JsonObject aggregatedData = context.getJsonObject(AGGREGATED_DATA);

        /*
         * The aggregated data used in the daily digest will always contain the latest
         * systems_with_suggestions value because email aggregations are processed in
         * ascending chronological order.
         */
        int systemsWithSuggestions = aggregation.getPayload().getJsonObject("context").getInteger(SYSTEMS_WITH_SUGGESTIONS);
        aggregatedData.put(SYSTEMS_WITH_SUGGESTIONS, systemsWithSuggestions);

        JsonArray events = aggregation.getPayload().getJsonArray("events");
        /*
         * The ROS payload only contain one event, but it doesn't hurt to iterate over
         * the events array.
         */
        for (int i = 0; i < events.size(); i++) {
            JsonObject payload = events.getJsonObject(i).getJsonObject("payload");

            String inventoryId = payload.getString("inventory_id");
            if (inventoryId == null || inventoryId.isBlank()) {
                Log.warn("Missing or blank inventory_id field found in resource-optimization aggregation payload");
            }

            /*
             * For each system, we'll only keep the latest current_state field value.
             */
            currentStates.put(inventoryId, payload.getString("current_state"));
        }

        aggregatedData.put(SYSTEMS_TRIGGERED, currentStates.keySet().size());

        /*
         * This transforms the currentStates map into another map where each state becomes a key
         * and the associated value is the number of systems that currently are in that state.
         */
        Map<String, Long> stateSystemCounts = currentStates.entrySet().stream()
                .collect(groupingBy(Map.Entry::getValue, counting()));

        /*
         * The states array is rebuilt on each invocation of this method.
         */
        JsonArray states = new JsonArray();
        for (Map.Entry<String, Long> stateSystemCount : stateSystemCounts.entrySet()) {
            JsonObject state = new JsonObject();
            state.put(STATE, stateSystemCount.getKey());
            state.put(SYSTEM_COUNT, stateSystemCount.getValue());
            states.add(state);
        }
        aggregatedData.put(STATES, states);
    }
}
