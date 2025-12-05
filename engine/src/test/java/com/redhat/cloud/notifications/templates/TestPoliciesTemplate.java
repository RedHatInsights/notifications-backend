package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPoliciesTemplate extends EmailTemplatesInDbHelper {

    private static final String EVENT_TYPE_NAME = "policy-triggered";

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Policies - Red Hat Enterprise Linux", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInstantEmailBody(boolean ignoreUserPreferences) {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");
        String result = generateEmailBody(EVENT_TYPE_NAME, action, ignoreUserPreferences);
        assertTrue(result.contains(TestHelpers.policyId1), "Body should contain policy id" + TestHelpers.policyId1);
        assertTrue(result.contains(TestHelpers.policyName1), "Body should contain policy name" + TestHelpers.policyName1);

        assertTrue(result.contains(TestHelpers.policyId2), "Body should contain policy id" + TestHelpers.policyId2);
        assertTrue(result.contains(TestHelpers.policyName2), "Body should contain policy name" + TestHelpers.policyName2);

        // Display name
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));

        assertEquals(ignoreUserPreferences, result.contains("This email was sent by Red Hat Hybrid Cloud Console, it is critical or requires action."));
        assertEquals(ignoreUserPreferences, result.contains("Therefore, you are receiving this message regardless of your email preferences."));
        assertNotEquals(ignoreUserPreferences, result.contains("This email was sent by Red Hat Hybrid Cloud Console |"));
        assertNotEquals(ignoreUserPreferences, result.contains("Manage email preferences"));
    }

    @Test
    void testDailyEmailTitleMultiplePoliciesAndSystems() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("unique_system_count", 2);

        Map<String, Object> policy02 = new HashMap<>();
        policy01.put("policy_id", "policy-02");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policy03 = new HashMap<>();
        policy01.put("policy_id", "policy-03");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);
        policies.put("policy-02", policy02);
        policies.put("policy-03", policy03);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 3);
    }

    @Test
    void testDailyEmailTitleOnePoliciesAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("policy_name", "My policy 01");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 1);
    }

    @Test
    void testDailyEmailBodyMultiplePoliciesAndSystems() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("policy_name", "My policy 01");
        policy01.put("unique_system_count", 2);

        Map<String, Object> policy02 = new HashMap<>();
        policy02.put("policy_id", "policy-02");
        policy02.put("policy_name", "My policy 02");
        policy02.put("unique_system_count", 1);

        Map<String, Object> policy03 = new HashMap<>();
        policy03.put("policy_id", "policy-03");
        policy03.put("policy_name", "My policy 03");
        policy03.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);
        policies.put("policy-02", policy02);
        policies.put("policy-03", policy03);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 3);

        String result = generateAggregatedEmailBody(payload);
        assertTrue(result.contains("Review the 3 policies that triggered 3 unique systems"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testDailyEmailBodyOnePoliciesAndOneSystem() {
        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("policy_name", "My policy 01");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 1);

        String result = generateAggregatedEmailBody(payload);
        assertTrue(result.contains("Review the 1 policy that triggered 1 system"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
