package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

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

        JsonObject context = new JsonObject();
        context.put("productId", productId);
        context.put("metricId", metricId);
        context.put("billingAccountId", billingAccountId);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setPayload(JsonObject.of("context", context).encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isPresent());

        // Parse the JSON key to verify it contains all expected fields
        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        assertEquals(orgId, deduplicationKeyJson.getString("orgId"));
        assertEquals(productId, deduplicationKeyJson.getString("productId"));
        assertEquals(metricId, deduplicationKeyJson.getString("metricId"));
        assertEquals(billingAccountId, deduplicationKeyJson.getString("billingAccountId"));
        assertEquals("2025-11", deduplicationKeyJson.getString("month"));
    }

    @Test
    void testGetDeduplicationKeyDifferentEventsInSameMonth() {

        JsonObject context1 = new JsonObject();
        context1.put("productId", "product-1");
        context1.put("metricId", "metric-1");
        context1.put("billingAccountId", "billing-1");

        Event event1 = new Event();
        event1.setOrgId("org-1");
        event1.setPayload(JsonObject.of("context", context1).encode());
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        JsonObject context2 = new JsonObject();
        context2.put("productId", "product-2");
        context2.put("metricId", "metric-2");
        context2.put("billingAccountId", "billing-2");

        Event event2 = new Event();
        event2.setOrgId("org-2");
        event2.setPayload(JsonObject.of("context", context2).encode());
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig2 = new SubscriptionsDeduplicationConfig(event2);
        Optional<String> deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertNotEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Different events should have different deduplication keys");
    }

    @Test
    void testGetDeduplicationKeySameEventDataShouldProduceSameKey() {

        String orgId = "test-org";
        String productId = "product-123";
        String metricId = "metric-456";
        String billingAccountId = "billing-789";

        JsonObject context1 = new JsonObject();
        context1.put("productId", productId);
        context1.put("metricId", metricId);
        context1.put("billingAccountId", billingAccountId);

        Event event1 = new Event();
        event1.setOrgId(orgId);
        event1.setPayload(JsonObject.of("context", context1).encode());
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        JsonObject context2 = new JsonObject();
        context2.put("productId", productId);
        context2.put("metricId", metricId);
        context2.put("billingAccountId", billingAccountId);

        Event event2 = new Event();
        event2.setOrgId(orgId);
        event2.setPayload(JsonObject.of("context", context2).encode());
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig2 = new SubscriptionsDeduplicationConfig(event2);
        Optional<String> deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Same event data should produce identical deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyIncludesCurrentMonth() {

        JsonObject context = new JsonObject();
        context.put("productId", "product");
        context.put("metricId", "metric");
        context.put("billingAccountId", "billing");

        Event event = new Event();
        event.setOrgId("org");
        event.setPayload(JsonObject.of("context", context).encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isPresent());

        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        String month = deduplicationKeyJson.getString("month");

        assertNotNull(month);
        assertTrue(month.matches("\\d{4}-\\d{2}"), "Month should be in yyyy-MM format");
    }
}
