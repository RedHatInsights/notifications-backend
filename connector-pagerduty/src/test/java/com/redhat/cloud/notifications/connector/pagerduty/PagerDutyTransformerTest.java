package com.redhat.cloud.notifications.connector.pagerduty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagerDutyTransformerTest {

    static final String ROUTING_KEY_VALUE = "my-routing-key";

    @Test
    void testMissingEventType() {
        JsonObject payload = createIncomingPayload();
        payload.remove(EVENT_TYPE);

        assertThrows(IllegalArgumentException.class, () ->
            PagerDutyTransformer.validatePayload(payload, DEFAULT_ORG_ID),
            "Event type must be specified for PagerDuty payload summary"
        );
    }

    @Test
    void testMissingApplication() {
        JsonObject payload = createIncomingPayload();
        payload.remove(APPLICATION);

        assertThrows(IllegalArgumentException.class, () ->
            PagerDutyTransformer.validatePayload(payload, DEFAULT_ORG_ID),
            "Application must be specified for PagerDuty payload source"
        );
    }

    @Test
    void testSuccessfulPayloadTransform() {
        JsonObject payload = createIncomingPayload();
        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("trigger", sent.getString(EVENT_ACTION));
        assertEquals(ROUTING_KEY_VALUE, sent.getString(ROUTING_KEY));
        assertEquals("default-application", sent.getString(CLIENT));
        assertNotNull(sent.getString(CLIENT_URL));

        JsonObject sentPayload = sent.getJsonObject(PAYLOAD);
        assertEquals("default-event-type", sentPayload.getString(SUMMARY));
        assertEquals("default-application", sentPayload.getString(SOURCE));
        assertEquals("default-bundle", sentPayload.getString(GROUP));
        assertEquals("error", sentPayload.getString(SEVERITY));

        JsonObject customDetails = sentPayload.getJsonObject(CUSTOM_DETAILS);
        assertEquals(DEFAULT_ACCOUNT_ID, customDetails.getString(ACCOUNT_ID));
        assertEquals(DEFAULT_ORG_ID, customDetails.getString(ORG_ID));
        assertNotNull(customDetails.getJsonObject(CONTEXT));
        assertNotNull(customDetails.getJsonObject(SOURCE_NAMES));
        assertEquals("IMPORTANT", customDetails.getString(RED_HAT_SEVERITY));
        assertNotNull(customDetails.getJsonArray(EVENTS));
    }

    @Test
    void testSuccessfulWithLegacyStaticSeverity() {
        JsonObject payload = createIncomingPayload(false);
        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, false);
        JsonObject sent = new JsonObject(result);

        JsonObject sentPayload = sent.getJsonObject(PAYLOAD);
        assertEquals("error", sentPayload.getString(SEVERITY));
        assertNull(sentPayload.getJsonObject(CUSTOM_DETAILS).getString(RED_HAT_SEVERITY));
    }

    @Test
    void testSuccessfulTestMessage() {
        JsonObject cloudEventPayload = JsonObject.of(
                "version", "2.0.0",
                "id", "13bb45fe-28a9-416d-81a1-aeb9abb24d41",
                "bundle", "console",
                "application", "integrations",
                "event_type", "integration-test",
                "timestamp", "2024-10-10T14:20:22.485454606",
                "account_id", DEFAULT_ACCOUNT_ID,
                "org_id", DEFAULT_ORG_ID,
                "context", JsonObject.of("integration-uuid", "e531af59-1c7f-4a6a-89e3-2a68954db8e9"),
                "events", JsonArray.of(
                        JsonObject.of(
                                "metadata", JsonObject.of("meta-information", "Meta information about the action"),
                                "payload", JsonObject.of("message", "A test alert, should be critical severity")
                        )
                )
        );
        cloudEventPayload.put("recipients", JsonArray.of());
        cloudEventPayload.put("application_url", "https://console.redhat.com/settings/integrations?from=notifications&integration=pagerduty");
        cloudEventPayload.put("severity", "MODERATE");

        String result = PagerDutyTransformer.buildPagerDutyPayload(cloudEventPayload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("trigger", sent.getString(EVENT_ACTION));
        assertEquals("integrations", sent.getString(CLIENT));
        assertEquals("integration-test", sent.getJsonObject(PAYLOAD).getString(SUMMARY));
        assertEquals("warning", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testSuccessfulIqeTestMessage() {
        JsonObject cloudEventPayload = JsonObject.of(
                "account_id", "default-account-id",
                "application", "inventory",
                "bundle", "rhel",
                "context", JsonObject.of(
                    "inventory_id", "85094ed1-1c52-4bc5-8e3e-4ea3869a17ce",
                    "hostname", "rhiqe.2349fj.notif-test",
                    "display_name", "rhiqe.2349fj.notif-test",
                    "rhel_version", "8.0"
                ),
                "event_type", "new-system-registered",
                "events", JsonArray.of(),
                "org_id", "default-org-id",
                "timestamp", "2020-10-03T15:22:13.000000025",
                "source", JsonObject.of(
                        "application", JsonObject.of("display_name", "Inventory"),
                        "bundle", JsonObject.of("display_name", "Red Hat Enterprise Linux"),
                        "event_type", JsonObject.of("display_name", "New system registered")
                ),
                "environment_url", "https://localhost"
        );
        cloudEventPayload.put("inventory_url", "https://localhost/insights/inventory/85094ed1-1c52-4bc5-8e3e-4ea3869a17ce?from=notifications&integration=pagerduty");
        cloudEventPayload.put("application_url", "https://localhost/insights/inventory?from=notifications&integration=pagerduty");
        cloudEventPayload.put("severity", "IMPORTANT");

        String result = PagerDutyTransformer.buildPagerDutyPayload(cloudEventPayload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("trigger", sent.getString(EVENT_ACTION));
        assertEquals("inventory", sent.getString(CLIENT));
        assertNotNull(sent.getJsonArray(LINKS));
        assertEquals(1, sent.getJsonArray(LINKS).size());
        assertEquals("error", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testInvalidTimestampDropped() {
        JsonObject payload = createIncomingPayload();
        payload.put(TIMESTAMP, "not a timestamp");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNull(sent.getJsonObject(PAYLOAD).getString(TIMESTAMP));
    }

    @Test
    void testMissingSourceNames() {
        JsonObject payload = createIncomingPayload();
        payload.remove(SOURCE);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNull(sent.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS).getJsonObject(SOURCE_NAMES));
    }

    @Test
    void testMissingApplicationDisplayName() {
        JsonObject payload = createIncomingPayload();
        JsonObject source = payload.getJsonObject(SOURCE);
        source.remove(APPLICATION);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        JsonObject sourceNames = sent.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS).getJsonObject(SOURCE_NAMES);
        assertNotNull(sourceNames);
        assertNull(sourceNames.getString(APPLICATION));
        assertNotNull(sourceNames.getString(BUNDLE));
    }

    @Test
    void testSourceWithoutDisplayNames() {
        JsonObject payload = createIncomingPayload();
        payload.put(SOURCE, new JsonObject());

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNull(sent.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS).getJsonObject(SOURCE_NAMES));
    }

    @Test
    void testMissingEvents() {
        JsonObject payload = createIncomingPayload();
        payload.remove(EVENTS);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNull(sent.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS).getJsonArray(EVENTS));
    }

    @Test
    void testWithHostUrl() {
        JsonObject payload = createIncomingPayload();
        JsonObject context = payload.getJsonObject(CONTEXT);
        context.put("host_url", "https://console.redhat.com/insights/inventory/8a4a4f75-5319-4255-9eb5-1ee5a92efd7f");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNotNull(sent.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS).getJsonObject(CONTEXT).getString("host_url"));
    }

    @Test
    void testWithMissingClientDisplayName() {
        JsonObject payload = createIncomingPayload();
        JsonObject context = payload.getJsonObject(CONTEXT);
        context.remove(DISPLAY_NAME);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        assertNotNull(new JsonObject(result));
    }

    @Test
    void testWithMissingInventoryId() {
        JsonObject payload = createIncomingPayload();
        JsonObject context = payload.getJsonObject(CONTEXT);
        context.remove("inventory_id");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        assertNotNull(new JsonObject(result));
    }

    @Test
    void testMissingInventoryUrl() {
        JsonObject payload = createIncomingPayload();
        payload.remove(INVENTORY_URL);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertNull(sent.getJsonArray(LINKS));
    }

    @Test
    void testLegacyStaticSeverityFallback() {
        JsonObject payload = createIncomingPayload(false);
        String staticSeverity = payload.remove(PAGERDUTY_STATIC_SEVERITY).toString();
        payload.put(SEVERITY, staticSeverity);

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, false);
        JsonObject sent = new JsonObject(result);

        assertEquals("error", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testDynamicSeverityCritical() {
        JsonObject payload = createIncomingPayload();
        payload.put(SEVERITY, "CRITICAL");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("critical", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testDynamicSeverityLow() {
        JsonObject payload = createIncomingPayload();
        payload.put(SEVERITY, "LOW");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("info", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testDynamicSeverityModerate() {
        JsonObject payload = createIncomingPayload();
        payload.put(SEVERITY, "MODERATE");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("warning", sent.getJsonObject(PAYLOAD).getString(SEVERITY));
    }

    @Test
    void testTimestampFormatting() {
        JsonObject payload = createIncomingPayload();
        payload.put(TIMESTAMP, "2024-08-12T17:26:19");

        String result = PagerDutyTransformer.buildPagerDutyPayload(payload, ROUTING_KEY_VALUE, true);
        JsonObject sent = new JsonObject(result);

        assertEquals("2024-08-12T17:26:19.000+0000", sent.getJsonObject(PAYLOAD).getString(TIMESTAMP));
    }

    // --- Helper methods ---

    static JsonObject createIncomingPayload() {
        return createIncomingPayload(true);
    }

    static JsonObject createIncomingPayload(boolean dynamicSeverity) {
        JsonObject payload = new JsonObject();
        payload.put(ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
        payload.put(APPLICATION, "default-application");
        payload.put(BUNDLE, "default-bundle");
        payload.put(CONTEXT, JsonObject.of(
                DISPLAY_NAME, "console",
                "inventory_id", "8a4a4f75-5319-4255-9eb5-1ee5a92efd7f"
        ));
        payload.put(EVENT_TYPE, "default-event-type");
        payload.put(EVENTS, JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value"),
                JsonObject.of("event-3-key", "event-3-value"),
                JsonObject.of("event-4-with-metadata-and-payload", JsonObject.of(
                        "metadata", "some metadata could be placed here",
                        "payload", "Here is a test payload message"
                ))
        ));
        payload.put(ORG_ID, DEFAULT_ORG_ID);
        payload.put(TIMESTAMP, LocalDateTime.of(2024, 8, 12, 17, 26, 19).toString());

        JsonObject source = JsonObject.of(
                APPLICATION, JsonObject.of(DISPLAY_NAME, "Default Application Name"),
                BUNDLE, JsonObject.of(DISPLAY_NAME, "Default Bundle Name"),
                EVENT_TYPE, JsonObject.of(DISPLAY_NAME, "Default Event Type Name")
        );

        payload.put(SOURCE, source);
        payload.put(INVENTORY_URL, "https://console.redhat.com/insights/inventory/8a4a4f75-5319-4255-9eb5-1ee5a92efd7f?from=notifications&integration=pagerduty");
        payload.put(APPLICATION_URL, "https://console.redhat.com/insights/default-application?from=notifications&integration=pagerduty");
        if (dynamicSeverity) {
            payload.put(SEVERITY, "IMPORTANT");
        } else {
            payload.put(PAGERDUTY_STATIC_SEVERITY, PagerDutySeverity.ERROR);
        }

        return payload;
    }
}
