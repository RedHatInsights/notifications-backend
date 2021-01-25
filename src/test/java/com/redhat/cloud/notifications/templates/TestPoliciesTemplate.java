package com.redhat.cloud.notifications.templates;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPoliciesTemplate {

    @Test
    public void testInstantEmailTitle() {
        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> payload = new HashMap<>();
        payload.put("triggers", triggers);
        payload.put("display_name", "FooMachine");
        payload.put("system_check_in", "2020-04-16T16:10:42.199046");

        String result = Policies.Templates.instantEmailTitle()
                .data("payload", payload)
                .render();

        assertTrue(result.contains("2"), "Title contains the number of policies triggered");
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
    }

    @Test
    public void testInstantEmailBody() {

        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> payload = new HashMap<>();
        payload.put("triggers", triggers);
        payload.put("display_name", "FooMachine");
        payload.put("system_check_in", "2020-04-16T16:10:42.199046");

        String result = Policies.Templates.instantEmailBody()
                .data("payload", payload)
                .render();

        for (String key : triggers.keySet()) {
            String value = triggers.get(key);
            assertTrue(result.contains(key), "Body should contain trigger key '" + key + "'");
            assertTrue(result.contains(value), "Body should contain trigger value '" + value + "'");
        }

        // Display name
        assertTrue(result.contains("FooMachine"), "Body should contain the display_name");
        assertFalse(result.contains("NOT_FOUND"), "A replacement was not correctly done");
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
                .data("payload", payload)
                .render();

        assertEquals("22 Apr 2021 - 3 policies triggered on 3 unique systems", result);
    }

    @Test
    public void testDailyEmailTitleOnePoliciesAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 1);

        String result = Policies.Templates.dailyEmailTitle()
                .data("payload", payload)
                .render();

        assertEquals("22 Apr 2021 - 1 policy triggered on 1 system", result);
    }

    @Test
    public void testDailyEmailBodyMultiplePoliciesAndSystems() {

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

        String result = Policies.Templates.dailyEmailBody()
                .data("payload", payload)
                .render();

        assertTrue(result.contains("<strong>3 policies</strong> triggered on <strong>3 unique systems</strong>"));
    }

    @Test
    public void testDailyEmailBodyOnePoliciesAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 1);

        String result = Policies.Templates.dailyEmailBody()
                .data("payload", payload)
                .render();

        assertTrue(result.contains("<strong>1 policy</strong> triggered on <strong>1 system</strong>"));
    }

}
