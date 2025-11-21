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

        Event event = givenSubscriptionEvent(orgId, productId, metricId, billingAccountId);
        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isPresent());

        // Parse the JSON key to verify it contains all expected fields
        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        assertEquals(orgId, deduplicationKeyJson.getString("org_id"));
        assertEquals(productId, deduplicationKeyJson.getString("product_id"));
        assertEquals(metricId, deduplicationKeyJson.getString("metric_id"));
        assertEquals(billingAccountId, deduplicationKeyJson.getString("billing_account_id"));
        assertEquals("2025-11", deduplicationKeyJson.getString("month"));
    }

    @Test
    void testGetDeduplicationKeyDifferentEventsInSameMonth() {

        Event event1 = givenSubscriptionEvent("org-1", "product-1", "metric-1", "billing-1");
        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        Event event2 = givenSubscriptionEvent("org-2", "product-2", "metric-2", "billing-2");
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

        Event event1 = givenSubscriptionEvent(orgId, productId, metricId, billingAccountId);
        SubscriptionsDeduplicationConfig deduplicationConfig1 = new SubscriptionsDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        Event event2 = givenSubscriptionEvent(orgId, productId, metricId, billingAccountId);
        SubscriptionsDeduplicationConfig deduplicationConfig2 = new SubscriptionsDeduplicationConfig(event2);
        Optional<String> deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Same event data should produce identical deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyIncludesCurrentMonth() {

        Event event = givenSubscriptionEvent("org", "product", "metric", "billing");
        SubscriptionsDeduplicationConfig deduplicationConfig = new SubscriptionsDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isPresent());

        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        String month = deduplicationKeyJson.getString("month");

        assertNotNull(month);
        assertTrue(month.matches("\\d{4}-\\d{2}"), "Month should be in yyyy-MM format");
    }

    private Event givenSubscriptionEvent(String orgId, String productId, String metricId, String billingAccountId) {
        JsonObject context = new JsonObject();
        context.put("product_id", productId);
        context.put("metric_id", metricId);
        context.put("billing_account_id", billingAccountId);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setPayload(JsonObject.of("context", context).encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));
        return event;
    }
}
