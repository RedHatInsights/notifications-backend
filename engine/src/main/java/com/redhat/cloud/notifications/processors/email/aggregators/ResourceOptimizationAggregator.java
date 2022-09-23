package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

public class ResourceOptimizationEmailAggregator extends AbstractEmailPayloadAggregator {

    private static final String ROS_KEY = "ros";
    private static final String CONTEXT_KEY = "context";
    private static final String PAYLOAD_KEY = "payload";
    private static final String SYSTEM_COUNT = "system_count";
    private static final String CURRENT_STATE = "current_state";
    private static final String SYSTEM_WITH_SUGGESTIONS = "systems_with_suggestions";

    ResourceOptimizationEmailAggregator(){
        JsonObject ros = new JsonObject();
        ros.put(STATE_COUNT, new JsonObject())
        context.put(ROS_KEY, ros);
    }

    void processEmailAggregation(EmailAggregation notification) {

        JsonObject ros = context.getJsonObject(ROS_KEY);
        JsonObject notificationJson = notification.getPayload();
        JsonObject event = notificationJson.getJsonObject()[0];
        JsonObject context = notificationJson.getJsonObject(CONTEXT_KEY);
        JsonObject systems_with_suggestions = context.getJsonObject(SYSTEM_WITH_SUGGESTIONS);
        JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
        String current_state = payload.getString(CURRENT_STATE);

        Map<String, Integer> state_system_map = new HashMap<>();

        if(state_system_map.containsKey(current_state)){
            state_system_map.put(current_state, state_system_map.get(current_state)+1);
        }
        else {
            state_system.put(current_state, 1);
        }

        for(Map.Entry<String, Integer> object : state_system_map.entrySet()){
            ros.getJsonObject(SYSTEM_COUNT).put(object.getKey(), object.getValue());
        }

        context.put(SYSTEM_WITH_SUGGESTIONS, systems_with_suggestions);
    }

}