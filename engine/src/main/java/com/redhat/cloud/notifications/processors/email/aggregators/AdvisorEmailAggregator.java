package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/*
 * Events are of the form:
 * {
   "bundle":"rhel",
   "application":"advisor",
   "event_type":"new-recommendation",
   "timestamp":"2022-11-30T17:30:20.773398",
   "account_id":"5758117",
   "org_id":"7806094",
   "context":{
      "inventory_id":"6ad30f3e-0497-4e74-99f1-b3f9a6120a6f",
      "hostname":"my-computer-jmartine",
      "display_name":"my-computer-jmartine",
      "rhel_version":"8.4",
      "host_url":"https://console.redhat.com/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f",
      "tags":[]
   },
   "events":[
      {
         "metadata":{},
         "payload":{
            "rule_id":"insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE",
            "rule_description":"System is not able to get the latest recommendations and may miss bug fixes when the Insights Client Core egg file is outdated",
            "total_risk":"2",
            "publish_date":"2021-03-13T18:44:00+00:00",
            "rule_url":"https://console.redhat.com/insights/advisor/recommendations/insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE/",
            "reboot_required":false,
            "has_incident":false,
            "report_url":"https://console.redhat.com/insights/advisor/recommendations/insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f"
         }
      }
   ]
}
 */

public class AdvisorEmailAggregator extends AbstractEmailPayloadAggregator {

    // Notification common
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";
    private static final String INVENTORY_ID = "inventory_id";
    private static final String PAYLOAD_RULE_ID = "rule_id";
    private static final String PAYLOAD_RULE_DESCRIPTION = "rule_description";
    private static final String PAYLOAD_RULE_TOTAL_RISK = "total_risk";
    private static final String PAYLOAD_RULE_HAS_INCIDENT = "has_incident";
    private static final String PAYLOAD_RULE_URL = "rule_url";

    // Advisor event payloads
    private static final String NEW_RECOMMENDATION = "new_recommendation";
    private static final String RESOLVED_RECOMMENDATION = "resolved_recommendation";
    private static final String DEACTIVATED_RECOMMENDATION = "resolved_recommendation";

    // Advisor events aggregator data contents
    private static final String ADVISOR_KEY = "advisor";
    private static final String NEW_RECOMMENDATIONS = "new_recommendations";
    private static final String RESOLVED_RECOMMENDATIONS = "resolved_recommendations";
    private static final String DEACTIVATED_RECOMMENDATIONS = "deactivated_recommendations";
    private static final String CONTENT_RULE_DESCRIPTION = "description";
    private static final String CONTENT_RULE_TOTAL_RISK = "total_risk";
    private static final String CONTENT_RULE_HAS_INCIDENT = "has_incident";
    private static final String CONTENT_RULE_URL = "url";
    private static final String CONTENT_SYSTEM_COUNT = "systems";

    // Advisor event types
    private static final String NEW_RECOMMENDATION_EVENT = "new-recommendation";
    private static final String RESOLVED_RECOMMENDATION_EVENT = "resolved-recommendation";
    private static final String DEACTIVATED_RULE_EVENT = "deactivated-rule";
    private static final Set<String> EVENT_TYPES = new HashSet<>(Arrays.asList(
        NEW_RECOMMENDATION_EVENT, RESOLVED_RECOMMENDATION_EVENT,
        DEACTIVATED_RULE_EVENT
    ));

    // Advisor final payload helpers
    private final Map<String, Map<String, Object>> newRecommendations = new HashMap<>();
    private final Map<String, Map<String, Object>> resolvedRecommendations = new HashMap<>();
    private final Map<String, Map<String, String>> deactivatedRecommendations = new HashMap<>();

    public AdvisorEmailAggregator() {
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
        /* Major assumption: that there is at most one event per day from each
         * rule,system pair - i.e. that the tuple (day, rule, system) is
         * unique for new, and resolved, events.  This is generally true,
         * because a rule would have to be new twice or resolved twice - i.e.
         * new, then resolved, then occur again - on the same system in order
         * for this to not be wrong.  This means we don't need to remember the
         * inventory ID of each system impacted by or resolving a rule. */

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        notifPayload.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            String ruleId = payload.getString(PAYLOAD_RULE_ID);
            String ruleDescription = payload.getString(PAYLOAD_RULE_DESCRIPTION);
            String ruleRisk = payload.getString(PAYLOAD_RULE_TOTAL_RISK);
            String ruleIncident = payload.getString(PAYLOAD_RULE_HAS_INCIDENT);
            String ruleURL = payload.getString(PAYLOAD_RULE_URL);

            switch (eventType) {
                case NEW_RECOMMENDATION:
                    Map<String, Object> ruleData = newRecommendations.computeIfAbsent(
                        ruleId, key -> new HashMap<>(Map.of(
                            CONTENT_RULE_DESCRIPTION, ruleDescription,
                            CONTENT_RULE_HAS_INCIDENT, ruleIncident,
                            CONTENT_RULE_TOTAL_RISK, ruleRisk,
                            CONTENT_RULE_URL, ruleURL,
                            CONTENT_SYSTEM_COUNT, 0
                        ))
                    );
                    ruleData.put(
                        CONTENT_SYSTEM_COUNT, (Integer) ruleData.get(CONTENT_SYSTEM_COUNT) + 1
                    );
                    break;
                case RESOLVED_RECOMMENDATION:
                    Map<String, Object> ruleData = resolvedRecommendations.computeIfAbsent(
                        ruleId, key -> new HashMap<>(Map.of(
                            CONTENT_RULE_DESCRIPTION, ruleDescription,
                            CONTENT_RULE_HAS_INCIDENT, ruleIncident,
                            CONTENT_RULE_TOTAL_RISK, ruleRisk,
                            CONTENT_RULE_URL, ruleURL,
                            CONTENT_SYSTEM_COUNT, 0
                        ))
                    );
                    ruleData.put(
                        CONTENT_SYSTEM_COUNT, (Integer) ruleData.get(CONTENT_SYSTEM_COUNT) + 1
                    );
                    break;
                case DEACTIVATED_RECOMMENDATION:
                    deactivatedRecommendations.computeIfAbsent(
                        ruleId, key -> new HashMap<>(Map.of(
                            CONTENT_RULE_DESCRIPTION, ruleDescription,
                            CONTENT_RULE_HAS_INCIDENT, ruleIncident,
                            CONTENT_RULE_TOTAL_RISK, ruleRisk,
                            CONTENT_RULE_URL, ruleURL
                        ))
                    );
                    break;
                default:
                    break;
            }
        });
    }
}
