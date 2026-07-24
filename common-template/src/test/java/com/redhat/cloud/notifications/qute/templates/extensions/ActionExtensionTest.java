package com.redhat.cloud.notifications.qute.templates.extensions;

import com.redhat.cloud.notifications.ingress.Action;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionExtensionTest {

    @Test
    void testToPrettyJsonFromAction() {
        Action action = Action.builder()
            .withBundle("console")
            .withApplication("notifications")
            .withEventType("instant-email")
            .withTimestamp(LocalDateTime.of(2026, 7, 24, 12, 0))
            .withOrgId("org-123")
            .withEvents(List.of())
            .build();

        String prettyJson = ActionExtension.toPrettyJson(action);

        assertTrue(prettyJson.contains("\"bundle\" : \"console\""));
        assertTrue(prettyJson.contains("\"application\" : \"notifications\""));
        assertTrue(prettyJson.contains("\"org_id\" : \"org-123\""));
        assertTrue(prettyJson.contains("\n"), "Pretty-printed JSON should be multi-line");
    }

    @Test
    void testToPrettyJsonFromMap() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("bundle", "console");
        action.put("application", "notifications");
        action.put("org_id", "org-123");

        String prettyJson = ActionExtension.toPrettyJson(action);

        assertTrue(prettyJson.contains("\"bundle\" : \"console\""));
        assertTrue(prettyJson.contains("\"application\" : \"notifications\""));
        assertTrue(prettyJson.contains("\"org_id\" : \"org-123\""));
        assertTrue(prettyJson.contains("\n"), "Pretty-printed JSON should be multi-line");
    }

    @Test
    void testToPrettyJsonFromMapWithNestedStructures() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("bundle", "console");
        action.put("context", Map.of("display_name", "Some Resource"));
        action.put("events", List.of(Map.of("payload", Map.of("key", "value"))));

        String prettyJson = ActionExtension.toPrettyJson(action);

        assertTrue(prettyJson.contains("\"display_name\" : \"Some Resource\""));
        assertTrue(prettyJson.contains("\"key\" : \"value\""));
    }

    @Test
    void testToPrettyJsonFromEmptyMap() {
        String prettyJson = ActionExtension.toPrettyJson(Map.of());
        assertTrue(prettyJson.contains("{ }") || prettyJson.trim().equals("{ }"));
    }

    @Test
    void testToPrettyJsonFromNullMap() {
        String prettyJson = ActionExtension.toPrettyJson((Map<String, Object>) null);
        assertEquals("null", prettyJson);
    }
}
