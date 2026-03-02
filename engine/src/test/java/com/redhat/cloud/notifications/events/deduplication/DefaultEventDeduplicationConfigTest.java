package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEventDeduplicationConfigTest {

    private final DefaultEventDeduplicationConfig deduplicationConfig = new DefaultEventDeduplicationConfig();

    @Test
    void testGetDeduplicationKeyWithNullEventId() {

        Event event = new Event();
        // Event ID is null by default.

        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey.isEmpty(), "The default deduplication key should be empty when event ID is null");
    }

    @Test
    void testGetDeduplicationKeyWithNonNullEventId() {

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);

        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey(event);

        assertTrue(deduplicationKey.isPresent());
        assertEquals(eventId.toString(), deduplicationKey.get());
    }

    @Test
    void testGetDeduplicationKeyWithDifferentEvents() {

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);

        Optional<String> deduplicationKey1 = deduplicationConfig.getDeduplicationKey(event1);

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);

        Optional<String> deduplicationKey2 = deduplicationConfig.getDeduplicationKey(event2);

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertNotEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Different events should have different deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyWithSameData() {

        UUID eventId = UUID.randomUUID();

        Event event1 = new Event();
        event1.setId(eventId);

        Optional<String> deduplicationKey1 = deduplicationConfig.getDeduplicationKey(event1);

        Event event2 = new Event();
        event2.setId(eventId);

        Optional<String> deduplicationKey2 = deduplicationConfig.getDeduplicationKey(event2);

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Events with the same data should have the same deduplication key");
    }
}
