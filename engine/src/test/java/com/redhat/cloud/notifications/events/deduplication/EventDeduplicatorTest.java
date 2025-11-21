package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventDeduplicatorTest {

    @Inject
    EventDeduplicator eventDeduplicator;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void beforeEach() {
        entityManager
            .createNativeQuery("DELETE FROM event_deduplication")
            .executeUpdate();
    }

    @Test
    void testIsNewWithDefaultDeduplication() {

        EventType eventType = createEventType("test-bundle", "test-app");

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);
        event1.setEventType(eventType);
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        assertTrue(eventDeduplicator.isNew(event1), "New event should return true");

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);
        event2.setEventType(eventType);
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        assertTrue(eventDeduplicator.isNew(event2), "New event should return true");

        Event event3 = new Event();
        event3.setId(eventId2);
        event3.setEventType(eventType);
        event3.setEventWrapper(new EventWrapperAction(ActionBuilder.build(LocalDateTime.of(2025, 11, 14, 10, 52))));

        assertFalse(eventDeduplicator.isNew(event3), "Duplicate event should return false");
    }

    @Test
    void testIsNewWithSubscriptionsDeduplication() {

        EventType eventType = createEventType("subscription-services", "subscriptions");

        Event event1 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 11, 14, 10, 52),
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event1), "New subscriptions event should return true");

        Event event2 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 11, 15, 14, 30), // Different day, same month.
            "prod456",
            "metric789",
            "billing001");

        assertFalse(eventDeduplicator.isNew(event2), "Duplicate subscriptions event (same month) should return false");

        Event event3 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org999",
            eventType,
            LocalDateTime.of(2025, 11, 16, 9, 15), // Different day, still same month.
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event3), "Event with different orgId should return true");

        Event event4 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 12, 1, 10, 0), // Different month.
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event4), "Event with different month should return true");

        Event event5 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 11, 17, 11, 0),
            "prod999",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event5), "Event with different product_id should return true");

        Event event6 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 11, 18, 11, 0),
            "prod999",
            "metric999",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event6), "Event with different metric_id should return true");

        Event event7 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            LocalDateTime.of(2025, 11, 19, 11, 0),
            "prod999",
            "metric999",
            "billing999");

        assertTrue(eventDeduplicator.isNew(event7), "Event with different billing_account_id should return true");
    }

    @Transactional
    EventType createEventType(String bundleName, String appName) {
        Bundle bundle = new Bundle();
        bundle.setName(bundleName);
        bundle.setDisplayName(bundleName);
        entityManager.persist(bundle);

        Application app = new Application();
        app.setName(appName);
        app.setDisplayName(appName);
        app.setBundle(bundle);
        app.setBundleId(bundle.getId());
        entityManager.persist(app);

        EventType eventType = new EventType();
        eventType.setName("test-event-type");
        eventType.setDisplayName("test-event-type");
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        entityManager.persist(eventType);

        return eventType;
    }

    private static Event createSubscriptionsEvent(UUID eventId, String orgId, EventType eventType, LocalDateTime timestamp, String productId, String metricId, String billingAccountId) {

        JsonObject context = new JsonObject();
        context.put("product_id", productId);
        context.put("metric_id", metricId);
        context.put("billing_account_id", billingAccountId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setEventWrapper(new EventWrapperAction(ActionBuilder.build(timestamp)));
        event.setPayload(JsonObject.of("context", context).encode());

        return event;
    }
}
