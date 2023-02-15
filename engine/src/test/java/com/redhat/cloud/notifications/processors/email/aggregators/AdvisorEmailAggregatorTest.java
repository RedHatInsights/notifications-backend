package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.AdvisorTestHelpers;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

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
public class AdvisorEmailAggregatorTest {

    public static final Map<String, String> TEST_RULE_1 = Map.of(
            RULE_ID, "test|Active_rule_1",
            RULE_DESCRIPTION, "Active rule 1",
            TOTAL_RISK, "3",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_1"
    );
    public static final Map<String, String> TEST_RULE_2 = Map.of(
            RULE_ID, "test|Active_rule_2",
            RULE_DESCRIPTION, "Active rule 2",
            TOTAL_RISK, "1",
            HAS_INCIDENT, "false",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_2"
    );
    public static final Map<String, String> TEST_RULE_3 = Map.of(
            RULE_ID, "test|Active_rule_3",
            RULE_DESCRIPTION, "Active rule 3",
            TOTAL_RISK, "4",
            HAS_INCIDENT, "false",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_3"
    );
    public static final Map<String, String> TEST_RULE_4 = Map.of(
            RULE_ID, "test|Active_rule_4",
            RULE_DESCRIPTION, "Active rule 4",
            TOTAL_RISK, "4",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_4"
    );
    public static final Map<String, String> TEST_RULE_5 = Map.of(
            RULE_ID, "test|Active_rule_5",
            RULE_DESCRIPTION, "Active rule 5",
            TOTAL_RISK, "4",
            HAS_INCIDENT, "true",
            RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_5"
    );
    public static final Map<String, String> TEST_RULE_6 = Map.of(
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

    /**
     * Tests if the aggregator aggregates the provided rules in the highest
     * "total risk" order, that the number of stored rules in the aggregator
     * respects the limit, and that the rules are not altered, just transformed.
     */
    @Test
    void testAggregatedInRightOrder() {
        // Copy the fixture maps to modify the "testRisk" without risking to
        // affect any other test.
        final Map<String, String> testRule1 = new HashMap<>(TEST_RULE_1);
        final Map<String, String> testRule2 = new HashMap<>(TEST_RULE_2);
        final Map<String, String> testRule3 = new HashMap<>(TEST_RULE_3);
        final Map<String, String> testRule4 = new HashMap<>(TEST_RULE_4);
        final Map<String, String> testRule5 = new HashMap<>(TEST_RULE_5);

        testRule1.put(TOTAL_RISK, "45");
        testRule2.put(TOTAL_RISK, "50");
        testRule3.put(TOTAL_RISK, "20");
        testRule4.put(TOTAL_RISK, "65");
        testRule5.put(TOTAL_RISK, "16");

        // Create a large set of random test rules.
        final List<Map<String, String>> testRuleCollection = new ArrayList<>(25);
        final Random random = new Random();
        for (int i = 0; i < 20; i++) {
            testRuleCollection.add(
                Map.of(
                    RULE_ID, UUID.randomUUID().toString(),
                    RULE_DESCRIPTION, UUID.randomUUID().toString(),
                    TOTAL_RISK, String.valueOf(random.nextInt(15)),
                    HAS_INCIDENT, String.valueOf(random.nextBoolean()),
                    RULE_URL, UUID.randomUUID().toString()
                )
            );
        }

        // Include the five rules that should stand out and be considered as
        // the most critical ones.
        testRuleCollection.add(testRule1);
        testRuleCollection.add(testRule2);
        testRuleCollection.add(testRule3);
        testRuleCollection.add(testRule4);
        testRuleCollection.add(testRule5);

        // Create the aggregator which will hold all the test rules.
        final AdvisorEmailAggregator advisorEmailAggregator = new AdvisorEmailAggregator();

        // Shuffle the collection so that we end up with a random order for the
        // collection of rules.
        Collections.shuffle(testRuleCollection);

        // Create the new recommendations based off the
        for (final Map<String, String> testRule : testRuleCollection) {
            advisorEmailAggregator.aggregate(
                AdvisorTestHelpers.createEmailAggregation(NEW_RECOMMENDATION, testRule)
            );
        }

        // Shuffle the collection so that we end up with a random order for the
        // collection of rules.
        Collections.shuffle(testRuleCollection);

        for (final Map<String, String> testRule : testRuleCollection) {
            advisorEmailAggregator.aggregate(
                AdvisorTestHelpers.createEmailAggregation(RESOLVED_RECOMMENDATION, testRule)
            );
        }

        // Shuffle the collection so that we end up with a random order for the
        // collection of rules.
        Collections.shuffle(testRuleCollection);

        for (final Map<String, String> testRule : testRuleCollection) {
            advisorEmailAggregator.aggregate(
                AdvisorTestHelpers.createEmailAggregation(DEACTIVATED_RECOMMENDATION, testRule)
            );
        }

        // Grab the resulting JSON.
        final JsonObject resultingAdvisor = JsonObject.mapFrom(advisorEmailAggregator.getContext()).getJsonObject(ADVISOR_KEY);

        final Map<String, Map<String, Object>> newRecommendations = resultingAdvisor.getJsonObject(NEW_RECOMMENDATIONS).mapTo(Map.class);
        final Map<String, Map<String, Object>> resolvedRecommendations = resultingAdvisor.getJsonObject(RESOLVED_RECOMMENDATIONS).mapTo(Map.class);
        final Map<String, Map<String, Object>> deactivatedRecommendations = resultingAdvisor.getJsonObject(DEACTIVATED_RECOMMENDATIONS).mapTo(Map.class);

        // The number of rules returned should be the maximum set in the
        // advisor's class.
        Assertions.assertEquals(AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS, newRecommendations.size(), "unexpected number of resulting rules for the new recommendations");
        Assertions.assertEquals(AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS, resolvedRecommendations.size(), "unexpected number of resulting rules for the resolved recommendations");
        Assertions.assertEquals(AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS, deactivatedRecommendations.size(), "unexpected number of resulting rules for the deactivated recommendations");

        // Add the test rules in the expected order that should be received,
        // sorted by total risk in descending order.
        final List<Map<String, String>> testRules = new ArrayList<>(AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS);
        testRules.add(testRule4);
        testRules.add(testRule2);
        testRules.add(testRule1);
        testRules.add(testRule3);
        testRules.add(testRule5);

        // Test that the new recommendations are equivalent to the test rules
        // and that they are in the correct order.
        final List<Map.Entry<String, Map<String, Object>>> newRecommendationsCollection = new ArrayList<>(newRecommendations.entrySet());
        this.assertRulesAreEqualAndCorrectOrder(testRules, newRecommendationsCollection);

        // Test that the resolved recommendations are equivalent to the test
        // rules and that they are in the correct order.
        final List<Map.Entry<String, Map<String, Object>>> resolvedRecommendationsCollection = new ArrayList<>(resolvedRecommendations.entrySet());
        this.assertRulesAreEqualAndCorrectOrder(testRules, resolvedRecommendationsCollection);

        // Test that the deactivated recommendations are equivalent to the test
        // rules and that they are in the correct order.
        final List<Map.Entry<String, Map<String, Object>>> deactivatedRecommendationsCollection = new ArrayList<>(deactivatedRecommendations.entrySet());
        this.assertRulesAreEqualAndCorrectOrder(testRules, deactivatedRecommendationsCollection);
    }

    /**
     * Tests that the map sorting and limiting function returns an ordered map
     * of a limited number of items.
     */
    @Test
    void testSortAndLimitMapByRisk() {
        final Map<String, Map<String, Object>> mapToSort = Map.of(
                "a", Map.of(TOTAL_RISK, "52"),
                "b", Map.of(TOTAL_RISK, "1"),
                "c", Map.of(TOTAL_RISK, "11"),
                "h", Map.of(TOTAL_RISK, "35"),
                "j", Map.of(TOTAL_RISK, "41"),
                "g", Map.of(TOTAL_RISK, "100"),
                "z", Map.of(TOTAL_RISK, "100")
        );

        final AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();

        // Call the function under test.
        final Map<String, Map<String, Object>> result = aggregator.sortAndLimitMapByRisk(mapToSort);

        Assertions.assertEquals(AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS, result.size(), "unexpected number of events returned. It didn't respect the specified maximum");

        // We use a list and not a set because the Set doesn't guarantee the
        // insertion order, and therefore the "expectedKeys" cannot be easily
        // generated with a "Set.of".
        final List<String> expectedKeys = List.of("g", "z", "a", "j", "h");
        final List<String> gotKeys = new ArrayList<>(result.keySet());

        // Still, the assertion could fail if two elements contain the same
        // "total risk" value, so we catch the first failure in case that order
        // is flipped. In case that it fails again, then we're sure that the
        // order is incorrect.
        try {
            Assertions.assertIterableEquals(expectedKeys, gotKeys);
        } catch (final AssertionFailedError e) {
            final List<String> secondExpectedKeys = List.of("z", "g", "a", "j", "h");

            try {
                Assertions.assertIterableEquals(secondExpectedKeys, gotKeys);
            } catch (final AssertionFailedError e2) {
                Assertions.fail(
                    String.format(
                        "unexpected order of keys received. Expected '%s' or '%s', got '%s'",
                            expectedKeys,
                            secondExpectedKeys,
                            gotKeys
                    )
                );
            }
        }

        final List<String> expectedRisks = List.of("100", "100", "52", "41", "35");
        final List<String> gotRisks = result
                .values()
                .stream()
                .map(stringObjectMap -> (String) stringObjectMap.get(TOTAL_RISK))
                .collect(Collectors.toList());

        Assertions.assertIterableEquals(expectedRisks, gotRisks);
    }

    /**
     * Asserts that the provided list of expected rules and the generated rules
     * by the aggregator are equivalent, and are in the correct order.
     * @param expectedRules the list of expected rules to be checked against.
     * @param gotRules the list of rules extracted from the aggregator.
     */
    private void assertRulesAreEqualAndCorrectOrder(final List<Map<String, String>> expectedRules, final List<Map.Entry<String, Map<String, Object>>> gotRules) {
        for (int i = 0; i < AdvisorEmailAggregator.MAXIMUM_NUMBER_RETURNED_EVENTS; i++) {
            final Map<String, String> expectedRule = expectedRules.get(i);
            final Map.Entry<String, Map<String, Object>> gotRule = gotRules.get(i);

            final String potentialErrorMessage = String.format("expected map '%s', got '%s'", expectedRule, gotRule);

            Assertions.assertEquals(expectedRule.get(RULE_ID), gotRule.getKey(), "unexpected rule ID. " + potentialErrorMessage);

            final Map<String, Object> aggregatedRule = gotRule.getValue();

            Assertions.assertEquals(expectedRule.get(RULE_DESCRIPTION), aggregatedRule.get(RULE_DESCRIPTION), "unexpected rule description. " + potentialErrorMessage);
            Assertions.assertEquals(expectedRule.get(HAS_INCIDENT), aggregatedRule.get(HAS_INCIDENT), "unexpected rule incident. " + potentialErrorMessage);
            Assertions.assertEquals(expectedRule.get(TOTAL_RISK), aggregatedRule.get(TOTAL_RISK), "unexpected rule total risk. " + potentialErrorMessage);
            Assertions.assertEquals(expectedRule.get(RULE_URL), aggregatedRule.get(RULE_URL), "unexpected rule url. " + potentialErrorMessage);
        }
    }
}
