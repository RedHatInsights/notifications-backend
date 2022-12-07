package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class AdvisorDailyDigestEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    // Notification common
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";
    private static final String INVENTORY_ID = "inventory_id";
    private static final String PAYLOAD_RULE_ID = "rule_id";

    // Advisor event payloads
    private static final String NEW_RECOMMENDATION = "new_recommendation";
    private static final String RESOLVED_RECOMMENDATION = "resolved_recommendation";
    private static final String DEACTIVATED_RECOMMENDATION = "resolved_recommendation";

    // Advisor events aggregator
    private static final String ADVISOR_KEY = "advisor";
    private static final String NEW_RECOMMENDATIONS = "new_recommendations";
    private static final String RESOLVED_RECOMMENDATIONS = "resolved_recommendations";
    private static final String DEACTIVATED_RECOMMENDATIONS = "deactivated_recommendations";

    // Advisor event types
    private static final String NEW_RECOMMENDATION_EVENT = "new-recommendation";
    private static final String RESOLVED_RECOMMENDATION_EVENT = "resolved-recommendation";
    private static final String DEACTIVATED_RULE_EVENT = "deactivated-rule";
    private static final Set<String> EVENT_TYPES = new HashSet<>(Arrays.asList(
        NEW_RECOMMENDATION_EVENT, RESOLVED_RECOMMENDATION_EVENT,
        DEACTIVATED_RULE_EVENT
    ));

    // Advisor final payload helpers
    private final Map<String, Set<String>> newRecommendations = new HashMap<>();
    private final Map<String, Set<String>> resolvedRecommendations = new HashMap<>();
    private final Set<String> deactivatedRecommendations = new HashSet<>();

    public AdvisorDailyDigestEmailPayloadAggregator() {
        JsonObject advisor = new JsonObject();
        advisor.put(NEW_RECOMMENDATIONS, newRecommendations);
        advisor.put(RESOLVED_RECOMMENDATIONS, resolvedRecommendations);
        advisor.put(DEACTIVATED_RECOMMENDATIONS, deactivatedRecommendations);

        context.put(ADVISOR_KEY, advisor);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notifPayload = notification.getPayload();
        JsonObject advisor = context.getJsonObject(ADVISOR_KEY);
        String eventType = notifPayload.getString(EVENT_TYPE_KEY);
        String inventoryId = notification.getContext().getString(INVENTORY_ID);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        notifPayload.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String ruleId = payload.getString(PAYLOAD_RULE_ID);

            if (eventType.equals(NEW_RECOMMENDATION)) {
                newRecommendations.computeIfAbsent(
                    ruleId, key -> new HashSet<String>()
                ).add(inventoryId);
            } else if (eventType.equals(RESOLVED_RECOMMENDATION)) {
                resolvedRecommendations.computeIfAbsent(
                    ruleId, key -> new HashSet<String>()
                ).add(inventoryId);
            } else if (eventType.equals(DEACTIVATED_RECOMMENDATION)) {
                deactivatedRecommendations.add(ruleId);
            }
        });
    }
}
