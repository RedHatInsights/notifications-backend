package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionsDeduplicationConfigTest {

    @Test
    void testGetDeleteAfter() {

        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        LocalDateTime deleteAfter = deduplicationConfig.getDeleteAfter();

        assertNotNull(deleteAfter);
        assertEquals(LocalDateTime.of(2025, 12, 1, 0, 0), deleteAfter);
    }

    @Test
    void testGetDeduplicationKey() {

        String orgId = "test-org-123";
        String productId = "product-456";
        String metricId = "metric-789";
        String billingAccountId = "billing-abc";

        JsonObject payload = new JsonObject();
        payload.put("orgId", orgId);
        payload.put("productId", productId);
        payload.put("metricId", metricId);
        payload.put("billingAccountId", billingAccountId);

        Event event = new Event();
        event.setPayload(payload.encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        String deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertNotNull(deduplicationKey);

        // Parse the JSON key to verify it contains all expected fields
        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey);
        assertEquals(orgId, deduplicationKeyJson.getString("orgId"));
        assertEquals(productId, deduplicationKeyJson.getString("productId"));
        assertEquals(metricId, deduplicationKeyJson.getString("metricId"));
        assertEquals(billingAccountId, deduplicationKeyJson.getString("billingAccountId"));
        assertEquals("2025-11", deduplicationKeyJson.getString("month"));
    }

    @Test
    void testGetDeduplicationKeyDifferentEventsInSameMonth() {

        JsonObject payload1 = new JsonObject();
        payload1.put("orgId", "org-1");
        payload1.put("productId", "product-1");
        payload1.put("metricId", "metric-1");
        payload1.put("billingAccountId", "billing-1");

        Event event1 = new Event();
        event1.setPayload(payload1.encode());
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        String deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        JsonObject payload2 = new JsonObject();
        payload2.put("orgId", "org-2");
        payload2.put("productId", "product-2");
        payload2.put("metricId", "metric-2");
        payload2.put("billingAccountId", "billing-2");

        Event event2 = new Event();
        event2.setPayload(payload2.encode());
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig2 = new SubscriptionsDeduplicationConfig(event2);
        String deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertNotNull(deduplicationKey1);
        assertNotNull(deduplicationKey2);
        assertNotEquals(deduplicationKey1, deduplicationKey2, "Different events should have different deduplication keys");
    }

    @Test
    void testGetDeduplicationKeySameEventDataShouldProduceSameKey() {

        String orgId = "test-org";
        String productId = "product-123";
        String metricId = "metric-456";
        String billingAccountId = "billing-789";

        JsonObject payload1 = new JsonObject();
        payload1.put("orgId", orgId);
        payload1.put("productId", productId);
        payload1.put("metricId", metricId);
        payload1.put("billingAccountId", billingAccountId);

        Event event1 = new Event();
        event1.setPayload(payload1.encode());
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        String deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        JsonObject payload2 = new JsonObject();
        payload2.put("orgId", orgId);
        payload2.put("productId", productId);
        payload2.put("metricId", metricId);
        payload2.put("billingAccountId", billingAccountId);

        Event event2 = new Event();
        event2.setPayload(payload2.encode());
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig2 = new SubscriptionsDeduplicationConfig(event2);
        String deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertEquals(deduplicationKey1, deduplicationKey2, "Same event data should produce identical deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyIncludesCurrentMonth() {

        JsonObject payload = new JsonObject();
        payload.put("orgId", "org");
        payload.put("productId", "product");
        payload.put("metricId", "metric");
        payload.put("billingAccountId", "billing");

        Event event = new Event();
        event.setPayload(payload.encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        String deduplicationKey = deduplicationConfig.getDeduplicationKey();
        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey);

        String month = deduplicationKeyJson.getString("month");
        assertNotNull(month);
        assertTrue(month.matches("\\d{4}-\\d{2}"), "Month should be in yyyy-MM format");
    }
}
