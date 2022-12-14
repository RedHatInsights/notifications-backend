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
 *
 * Output should be of the form:
 * {
    "new_recommendations": {
        "product_eol_check|NGINX_EOL_ERROR": {
            "description": "Red Hat has discontinued support services as well as software maintenance services for the End-Of-Life nginx",
            "total_risk": 4,
            "has_incident": false,
            "url": "https://console.redhat.com/insights/advisor/recommendations/product_eol_check|NGINX_EOL_ERROR",
            "affected_systems": 33
        },
        "container_rhel_version_privileged|CONTAINER_RHEL_VERSION_PRIVILEGED": {
            "description": "A privileged container running on a different RHEL version host is not compatible",
            "total_risk": 4,
            "has_incident": true,
            "url": "https://console.redhat.com/insights/advisor/recommendations/container_rhel_version_privileged|CONTAINER_RHEL_VERSION_PRIVILEGED",
            "affected_systems": 3
        }
    },
    "resolved_recommendations": {
        "auditd_daemon_log_file_oversize|AUDIT_DAEMON_LOG_FILE_OVERSIZE": {
            "description": "The auditd service gets suspended when the size of the log file is over \"max_log_file\"",
            "total_risk": 2,
            "has_incident": true,
            "url": "https://console.redhat.com/insights/advisor/recommendations/audit_daemon_log_file_oversize|AUDIT_DAEMON_LOG_FILE_OVERSIZE",
            "affected_systems": 1
        }
    },
    "deactivated_recommendations": {
        "el6_to_el7_upgrade|RHEL6_TO_RHEL7_UPGRADE_AVAILABLE_V4": {
            "description": "RHEL 6 system is eligible for an in-place upgrade to RHEL 7 using the Leapp utility",
            "has_incident": false,
            "url": "https://console.redhat.com/insights/advisor/recommendations/el6_to_el7_upgrade|RHEL6_TO_RHEL7_UPGRADE_AVAILABLE_V4",
            "total_risk": 3
        }
    }
}
 */

public class AdvisorEmailAggregator extends AbstractEmailPayloadAggregator {

    // Advisor event types
    public static final String NEW_RECOMMENDATION = "new-recommendation";
    public static final String RESOLVED_RECOMMENDATION = "resolved-recommendation";
    public static final String DEACTIVATED_RECOMMENDATION = "deactivated-recommendation";

    // Advisor events aggregator data contents
    public static final String ADVISOR_KEY = "advisor";
    public static final String NEW_RECOMMENDATIONS = "new_recommendations";
    public static final String RESOLVED_RECOMMENDATIONS = "resolved_recommendations";
    public static final String DEACTIVATED_RECOMMENDATIONS = "deactivated_recommendations";
    public static final String RULE_ID = "rule_id";
    public static final String RULE_DESCRIPTION = "rule_description";
    public static final String TOTAL_RISK = "total_risk";
    public static final String HAS_INCIDENT = "has_incident";
    public static final String RULE_URL = "rule_url";
    public static final String CONTENT_SYSTEM_COUNT = "systems";

    // Notification common
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    private static final Set<String> EVENT_TYPES = new HashSet<>(Arrays.asList(
            NEW_RECOMMENDATION, RESOLVED_RECOMMENDATION,
            DEACTIVATED_RECOMMENDATION
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
            String ruleId = payload.getString(RULE_ID);
            String ruleDescription = payload.getString(RULE_DESCRIPTION);
            String ruleRisk = payload.getString(TOTAL_RISK);
            String ruleIncident = payload.getString(HAS_INCIDENT);
            String ruleURL = payload.getString(RULE_URL);
            Map<String, Object> ruleData;

            switch (eventType) {
                case NEW_RECOMMENDATION:
                    ruleData = newRecommendations.computeIfAbsent(
                        ruleId, key -> new HashMap<>(Map.of(
                            RULE_DESCRIPTION, ruleDescription,
                            HAS_INCIDENT, ruleIncident,
                            TOTAL_RISK, ruleRisk,
                            RULE_URL, ruleURL,
                            CONTENT_SYSTEM_COUNT, 0
                        ))
                    );
                    ruleData.put(
                        CONTENT_SYSTEM_COUNT, (Integer) ruleData.get(CONTENT_SYSTEM_COUNT) + 1
                    );
                    break;
                case RESOLVED_RECOMMENDATION:
                    ruleData = resolvedRecommendations.computeIfAbsent(
                        ruleId, key -> new HashMap<>(Map.of(
                            RULE_DESCRIPTION, ruleDescription,
                            HAS_INCIDENT, ruleIncident,
                            TOTAL_RISK, ruleRisk,
                            RULE_URL, ruleURL,
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
                            RULE_DESCRIPTION, ruleDescription,
                            HAS_INCIDENT, ruleIncident,
                            TOTAL_RISK, ruleRisk,
                            RULE_URL, ruleURL
                        ))
                    );
                    break;
                default:
                    break;
            }
        });
    }
}
