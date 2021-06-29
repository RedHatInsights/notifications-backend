package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPoliciesTemplate {

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");
        String result = Policies.Templates.instantEmailTitle()
                .data("action", action)
                .render();

        assertTrue(result.contains("2"), "Title contains the number of policies triggered");
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");
        String result = Policies.Templates.instantEmailBody()
                .data("action", action)
                .render();

        assertTrue(result.contains(TestHelpers.policyId1), "Body should contain policy id" + TestHelpers.policyId1);
        assertTrue(result.contains(TestHelpers.policyName1), "Body should contain policy name" + TestHelpers.policyName1);

        assertTrue(result.contains(TestHelpers.policyId2), "Body should contain policy id" + TestHelpers.policyId2);
        assertTrue(result.contains(TestHelpers.policyName2), "Body should contain policy name" + TestHelpers.policyName2);

        // Display name
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
    }

    @Test
    public void testDailyEmailTitleMultiplePoliciesAndSystems() {

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

        String result = Policies.Templates.dailyEmailTitle()
                .data("action", Map.of("context", payload))
                .render();

        assertEquals("22 Apr 2021 - 3 policies triggered on 3 unique systems", result);
    }

    @Test
    public void testDailyEmailTitleOnePoliciesAndOneSystem() {

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

        String result = Policies.Templates.dailyEmailTitle()
                .data("action", Map.of("context", payload))
                .render();

        assertEquals("22 Apr 2021 - 1 policy triggered on 1 system", result);
    }

    @Test
    public void testDailyEmailBodyMultiplePoliciesAndSystems() {

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

        String result = Policies.Templates.dailyEmailBody()
                .data("action", Map.of("context", payload))
                .render();

        assertTrue(result.contains("<b>3 policies</b> triggered on <b>3 unique systems</b>"));
    }

    @Test
    public void testDailyEmailBodyOnePoliciesAndOneSystem() {

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

        String result = Policies.Templates.dailyEmailBody()
                .data("action", Map.of("context", payload))
                .render();

        assertTrue(result.contains("<b>1 policy</b> triggered on <b>1 system</b>"));
    }

}
