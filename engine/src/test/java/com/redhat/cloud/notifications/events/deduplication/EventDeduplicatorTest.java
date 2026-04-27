package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EventDeduplicatorTest {

    public static final String TEST_BUNDLE_NAME = "test-bundle";
    public static final String SUBSCRIPTION_SERVICES_BUNDLE_NAME = "subscription-services";

    static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    @Inject
    EventDeduplicator eventDeduplicator;

    @Inject
    EntityManager entityManager;

    @InjectSpy
    EngineConfig config;

    @BeforeEach
    @Transactional
    void beforeEach() {
        entityManager
            .createNativeQuery("DELETE FROM event_deduplication")
            .executeUpdate();
    }

    @AfterEach
    @Transactional
    void afterEach() {
        entityManager
                .createNativeQuery("DELETE FROM bundles WHERE name = :testName OR name = :subName")
                .setParameter("testName", TEST_BUNDLE_NAME)
                .setParameter("subName", SUBSCRIPTION_SERVICES_BUNDLE_NAME)
                .executeUpdate();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIsNewWithDefaultDeduplication(final boolean valkeyDedupEnabled) {
        when(config.isValkeyEventDeduplicatorEnabled()).thenReturn(valkeyDedupEnabled);
        when(config.isInMemoryDbEnabled()).thenReturn(valkeyDedupEnabled);

        EventType eventType = createEventType(TEST_BUNDLE_NAME, "test-app");
        LocalDateTime dateTime = LocalDateTime.now(UTC_ZONE);

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);
        event1.setEventType(eventType);
        event1.setEventWrapper(new EventWrapperAction(ActionBuilder.build(dateTime)));

        assertTrue(eventDeduplicator.isNew(event1), "New event should return true");

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);
        event2.setEventType(eventType);
        event2.setEventWrapper(new EventWrapperAction(ActionBuilder.build(dateTime)));

        assertTrue(eventDeduplicator.isNew(event2), "New event should return true");

        Event event3 = new Event();
        event3.setId(eventId2);
        event3.setEventType(eventType);
        event3.setEventWrapper(new EventWrapperAction(ActionBuilder.build(dateTime)));

        assertFalse(eventDeduplicator.isNew(event3), "Duplicate event should return false");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIsNewWithSubscriptionsDeduplication(final boolean valkeyDedupEnabled) {
        when(config.isValkeyEventDeduplicatorEnabled()).thenReturn(valkeyDedupEnabled);
        when(config.isInMemoryDbEnabled()).thenReturn(valkeyDedupEnabled);

        EventType eventType = createEventType(SUBSCRIPTION_SERVICES_BUNDLE_NAME, "subscriptions");
        LocalDateTime baseDateTime =
                LocalDateTime.of(LocalDateTime.now(UTC_ZONE).plusYears(1).getYear(), 11, 14, 10, 52);

        Event event1 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime,
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event1), "New subscriptions event should return true");

        Event event2 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime.withDayOfMonth(15).withHour(14).withMinute(30), // Different day, same month.
            "prod456",
            "metric789",
            "billing001");

        assertFalse(eventDeduplicator.isNew(event2), "Duplicate subscriptions event (same month) should return false");

        Event event3 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org999",
            eventType,
            baseDateTime.withDayOfMonth(16).withHour(9).withMinute(15), // Different day, still same month.
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event3), "Event with different orgId should return true");

        Event event4 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime.withMonth(12).withDayOfMonth(1).withHour(10).withMinute(0), // Different month.
            "prod456",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event4), "Event with different month should return true");

        Event event5 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime.withDayOfMonth(17).withHour(11).withMinute(0),
            "prod999",
            "metric789",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event5), "Event with different product_id should return true");

        Event event6 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime.withDayOfMonth(18).withHour(11).withMinute(0),
            "prod999",
            "metric999",
            "billing001");

        assertTrue(eventDeduplicator.isNew(event6), "Event with different metric_id should return true");

        Event event7 = createSubscriptionsEvent(
            UUID.randomUUID(),
            "org123",
            eventType,
            baseDateTime.withDayOfMonth(19).withHour(11).withMinute(0),
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
