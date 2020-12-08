package com.redhat.cloud.notifications.templates;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
    }
}
