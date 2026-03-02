package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class SubscriptionsDeduplicationConfigTest {

    @InjectSpy
    SubscriptionsDeduplicationConfig deduplicationConfig;

    @InjectMock
    EngineConfig engineConfig;

    @BeforeEach
    void setUp() {
        when(engineConfig.isSubscriptionsDeduplicationWillBeNotifiedEnabled(anyString())).thenReturn(true);
        // Default: org will not be notified
        doReturn(false).when(deduplicationConfig).willBeNotified(anyString(), any(UUID.class));
    }

    @Test
    void testGetDeleteAfter() {

        Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        LocalDateTime deleteAfter = deduplicationConfig.getDeleteAfter(event);

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
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey.isPresent());

        // Parse the JSON key to verify it contains all expected fields
        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        assertEquals(orgId, deduplicationKeyJson.getString("org_id"));
        assertEquals(productId, deduplicationKeyJson.getString("product_id"));
        assertEquals(metricId, deduplicationKeyJson.getString("metric_id"));
        assertEquals(billingAccountId, deduplicationKeyJson.getString("billing_account_id"));
        assertEquals("2025-11", deduplicationKeyJson.getString("month"));
        assertEquals(false, deduplicationKeyJson.getBoolean("will_be_notified"));
    }

    @Test
    void testGetDeduplicationKeyDifferentEventsInSameMonth() {

        Event event1 = givenSubscriptionEvent("org-1", "product-1", "metric-1", "billing-1");
        Optional<String> deduplicationKey1 = deduplicationConfig.getDeduplicationKey(event1);

        Event event2 = givenSubscriptionEvent("org-2", "product-2", "metric-2", "billing-2");
        Optional<String> deduplicationKey2 = deduplicationConfig.getDeduplicationKey(event2);

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
        Optional<String> deduplicationKey1 = deduplicationConfig.getDeduplicationKey(event1);

        Event event2 = givenSubscriptionEvent(orgId, productId, metricId, billingAccountId);
        Optional<String> deduplicationKey2 = deduplicationConfig.getDeduplicationKey(event2);

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Same event data should produce identical deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyIncludesCurrentMonth() {

        Event event = givenSubscriptionEvent("org", "product", "metric", "billing");
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey.isPresent());

        JsonObject deduplicationKeyJson = new JsonObject(deduplicationKey.get());
        String month = deduplicationKeyJson.getString("month");

        assertNotNull(month);
        assertTrue(month.matches("\\d{4}-\\d{2}"), "Month should be in yyyy-MM format");
    }

    @Test
    void testGetDeduplicationKeyWithFeatureFlagDisabled() {

        when(engineConfig.isSubscriptionsDeduplicationWillBeNotifiedEnabled(anyString())).thenReturn(false);

        Event event = givenSubscriptionEvent("test-org", "product-123", "metric-456", "billing-789");
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey.isPresent());
        JsonObject keyJson = new JsonObject(deduplicationKey.get());
        assertFalse(keyJson.containsKey("will_be_notified"), "will_be_notified should not be present when feature flag is disabled");
    }

    @Test
    void testGetDeduplicationKeyWithNotificationEligibility() {

        String orgId = "test-org";
        String productId = "product-123";
        String metricId = "metric-456";
        String billingAccountId = "billing-789";

        Event event = givenSubscriptionEvent(orgId, productId, metricId, billingAccountId);

        // When org will be notified
        doReturn(true).when(deduplicationConfig).willBeNotified(anyString(), any(UUID.class));
        Optional<String> deduplicationKey1 = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey1.isPresent());
        JsonObject keyJson1 = new JsonObject(deduplicationKey1.get());
        assertEquals(true, keyJson1.getBoolean("will_be_notified"));

        // When org will NOT be notified
        doReturn(false).when(deduplicationConfig).willBeNotified(anyString(), any(UUID.class));
        Optional<String> deduplicationKey2 = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey2.isPresent());
        JsonObject keyJson2 = new JsonObject(deduplicationKey2.get());
        assertEquals(false, keyJson2.getBoolean("will_be_notified"));

        // Keys should be different based on notification eligibility
        assertNotEquals(deduplicationKey1.get(), deduplicationKey2.get(),
                "Deduplication keys should differ based on notification eligibility");
    }

    private Event givenSubscriptionEvent(String orgId, String productId, String metricId, String billingAccountId) {
        JsonObject context = new JsonObject();
        context.put("product_id", productId);
        context.put("metric_id", metricId);
        context.put("billing_account_id", billingAccountId);

        EventType eventType = new EventType();
        eventType.setId(UUID.randomUUID());

        Event event = new Event();
        event.setOrgId(orgId);
        event.setPayload(JsonObject.of("context", context).encode());
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));
        event.setEventType(eventType);
        return event;
    }
}
