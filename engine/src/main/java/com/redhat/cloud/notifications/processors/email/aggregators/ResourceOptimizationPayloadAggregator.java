package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceOptimizationPayloadAggregator extends AbstractEmailPayloadAggregator {

    public static final String AGGREGATED_DATA = "aggregated_data";
    public static final String STATES = "states";
    public static final String RULES_TRIGGERED = "rules_triggered";
    public static final String SYSTEMS_WITH_SUGGESTIONS = "systems_with_suggestions";
    public static final String STATE = "state";
    public static final String SYSTEM_COUNT = "system_count";

    ResourceOptimizationPayloadAggregator() {
        context.put(AGGREGATED_DATA, new JsonObject());
        context.getJsonObject(AGGREGATED_DATA).put(STATES, new JsonArray());
        context.getJsonObject(AGGREGATED_DATA).put(RULES_TRIGGERED, 0);
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
        JsonArray states = aggregatedData.getJsonArray(STATES);

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
            JsonObject event = events.getJsonObject(i);

            /*
             * The number of daily rules triggered is incremented on each processed event.
             */
            aggregatedData.put(RULES_TRIGGERED, aggregatedData.getInteger(RULES_TRIGGERED) + 1);

            /*
             * The current_state field found in the event needs to be counted in the
             * aggregated_data.states array. An entry for that state may or may not already exist.
             */
            String currentState = event.getJsonObject("payload").getString("current_state");
            boolean found = false;

            /*
             * Let's try to find and update an existing entry.
             */
            for (int j = 0; j < states.size(); j++) {
                JsonObject statesEntry = states.getJsonObject(j);
                if (statesEntry.getString(STATE).equals(currentState)) {
                    /*
                     * The entry was found, its system_count field is incremented by 1.
                     */
                    found = true;
                    statesEntry.put(SYSTEM_COUNT, statesEntry.getInteger(SYSTEM_COUNT) + 1);
                    break;
                }
            }

            /*
             * The entry was not found, so it needs to be created with an initial
             * system_count field value of 1.
             */
            if (!found) {
                JsonObject statesEntry = new JsonObject();
                statesEntry.put(STATE, currentState);
                statesEntry.put(SYSTEM_COUNT, 1);
                states.add(statesEntry);
            }
        }
    }
}
