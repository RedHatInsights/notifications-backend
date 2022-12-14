package com.redhat.cloud.notifications.processors.email.aggregators;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.AdvisorTestHelpers.createEmailAggregation;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.ADVISOR_KEY;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.CONTENT_SYSTEM_COUNT;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATIONS;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.HAS_INCIDENT;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATIONS;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATIONS;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_DESCRIPTION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_URL;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.TOTAL_RISK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * Aggregated data should be of the form:
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
class AdvisorEmailAggregatorTest {

    private static final Map<String, String> TEST_RULE_1 = Map.of(
            RULE_ID, "test|Active_rule_1",
            RULE_DESCRIPTION, "Active rule 1",
            TOTAL_RISK, "3",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_1"
    );
    private static final Map<String, String> TEST_RULE_2 = Map.of(
            RULE_ID, "test|Active_rule_2",
            RULE_DESCRIPTION, "Active rule 2",
            TOTAL_RISK, "0",
            HAS_INCIDENT, "false",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_2"
    );
    private static final Map<String, String> TEST_RULE_3 = Map.of(
            RULE_ID, "test|Active_rule_3",
            RULE_DESCRIPTION, "Active rule 3",
            TOTAL_RISK, "0",
            HAS_INCIDENT, "false",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_3"
    );
    private static final Map<String, String> TEST_RULE_4 = Map.of(
            RULE_ID, "test|Active_rule_4",
            RULE_DESCRIPTION, "Active rule 4",
            TOTAL_RISK, "10",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_4"
    );
    private static final Map<String, String> TEST_RULE_5 = Map.of(
            RULE_ID, "test|Active_rule_5",
            RULE_DESCRIPTION, "Active rule 5",
            TOTAL_RISK, "4",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_5"
    );
    private static final Map<String, String> TEST_RULE_6 = Map.of(
            RULE_ID, "test|Active_rule_6",
            RULE_DESCRIPTION, "Active rule 6",
            TOTAL_RISK, "2",
            HAS_INCIDENT, "false",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_6"
    );

    @Test
    void testAggregate() {

        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        for (int i = 0; i < 4; i++) {
            aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_1));
        }
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_2));
        for (int i = 0; i < 11; i++) {
            aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_3));
        }
        aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_4));
        for (int i = 0; i < 7; i++) {
            aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_5));
        }
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_6));

        JsonObject advisor = JsonObject.mapFrom(aggregator.getContext()).getJsonObject(ADVISOR_KEY);

        Map<String, Map<String, Object>> newRecommendations = advisor.getJsonObject(NEW_RECOMMENDATIONS).mapTo(Map.class);
        assertEquals(2, newRecommendations.entrySet().size());

        Map<String, Object> aggregatedRule1 = newRecommendations.get(TEST_RULE_1.get(RULE_ID));
        assertEquals(TEST_RULE_1.get(RULE_DESCRIPTION), aggregatedRule1.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_1.get(HAS_INCIDENT), aggregatedRule1.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_1.get(TOTAL_RISK), aggregatedRule1.get(TOTAL_RISK));
        assertEquals(TEST_RULE_1.get(RULE_URL), aggregatedRule1.get(RULE_URL));
        assertEquals(4, aggregatedRule1.get(CONTENT_SYSTEM_COUNT));

        Map<String, Object> aggregatedRule2 = newRecommendations.get(TEST_RULE_2.get(RULE_ID));
        assertEquals(TEST_RULE_2.get(RULE_DESCRIPTION), aggregatedRule2.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_2.get(HAS_INCIDENT), aggregatedRule2.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_2.get(TOTAL_RISK), aggregatedRule2.get(TOTAL_RISK));
        assertEquals(TEST_RULE_2.get(RULE_URL), aggregatedRule2.get(RULE_URL));
        assertEquals(1, aggregatedRule2.get(CONTENT_SYSTEM_COUNT));

        Map<String, Map<String, Object>> resolvedRecommendations = advisor.getJsonObject(RESOLVED_RECOMMENDATIONS).mapTo(Map.class);
        assertEquals(2, resolvedRecommendations.size());

        Map<String, Object> aggregatedRule3 = resolvedRecommendations.get(TEST_RULE_3.get(RULE_ID));
        assertEquals(TEST_RULE_3.get(RULE_DESCRIPTION), aggregatedRule3.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_3.get(HAS_INCIDENT), aggregatedRule3.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_3.get(TOTAL_RISK), aggregatedRule3.get(TOTAL_RISK));
        assertEquals(TEST_RULE_3.get(RULE_URL), aggregatedRule3.get(RULE_URL));
        assertEquals(11, aggregatedRule3.get(CONTENT_SYSTEM_COUNT));

        Map<String, Object> aggregatedRule4 = resolvedRecommendations.get(TEST_RULE_4.get(RULE_ID));
        assertEquals(TEST_RULE_4.get(RULE_DESCRIPTION), aggregatedRule4.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_4.get(HAS_INCIDENT), aggregatedRule4.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_4.get(TOTAL_RISK), aggregatedRule4.get(TOTAL_RISK));
        assertEquals(TEST_RULE_4.get(RULE_URL), aggregatedRule4.get(RULE_URL));
        assertEquals(1, aggregatedRule4.get(CONTENT_SYSTEM_COUNT));

        Map<String, Map<String, Object>> deactivatedRecommendations = advisor.getJsonObject(DEACTIVATED_RECOMMENDATIONS).mapTo(Map.class);
        assertEquals(2, deactivatedRecommendations.size());

        Map<String, Object> aggregatedRule5 = deactivatedRecommendations.get(TEST_RULE_5.get(RULE_ID));
        assertEquals(TEST_RULE_5.get(RULE_DESCRIPTION), aggregatedRule5.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_5.get(HAS_INCIDENT), aggregatedRule5.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_5.get(TOTAL_RISK), aggregatedRule5.get(TOTAL_RISK));
        assertEquals(TEST_RULE_5.get(RULE_URL), aggregatedRule5.get(RULE_URL));
        assertNull(aggregatedRule5.get(CONTENT_SYSTEM_COUNT));

        Map<String, Object> aggregatedRule6 = deactivatedRecommendations.get(TEST_RULE_6.get(RULE_ID));
        assertEquals(TEST_RULE_6.get(RULE_DESCRIPTION), aggregatedRule6.get(RULE_DESCRIPTION));
        assertEquals(TEST_RULE_6.get(HAS_INCIDENT), aggregatedRule6.get(HAS_INCIDENT));
        assertEquals(TEST_RULE_6.get(TOTAL_RISK), aggregatedRule6.get(TOTAL_RISK));
        assertEquals(TEST_RULE_6.get(RULE_URL), aggregatedRule6.get(RULE_URL));
        assertNull(aggregatedRule6.get(CONTENT_SYSTEM_COUNT));
    }
}
