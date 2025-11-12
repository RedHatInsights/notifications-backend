package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void testIsNew() {

        EventType eventType = createEventType();

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);
        event1.setEventType(eventType);

        assertTrue(eventDeduplicator.isNew(event1), "New event should return true");

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);
        event2.setEventType(eventType);

        assertTrue(eventDeduplicator.isNew(event2), "New event should return true");

        Event event3 = new Event();
        event3.setId(eventId2);
        event3.setEventType(eventType);

        assertFalse(eventDeduplicator.isNew(event3), "Duplicate event should return false");
    }

    @Transactional
    EventType createEventType() {
        Bundle bundle = new Bundle();
        bundle.setName("test-bundle");
        bundle.setDisplayName("test-bundle");
        entityManager.persist(bundle);

        Application app = new Application();
        app.setName("test-app");
        app.setDisplayName("test-app");
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
}
