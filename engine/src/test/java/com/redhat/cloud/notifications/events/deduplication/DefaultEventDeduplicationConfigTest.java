package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.models.Event;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEventDeduplicationConfigTest {

    @Test
    void testGetDeduplicationKeyWithNullEventId() {

        Event event = new Event();
        // Event ID is null by default.

        DefaultEventDeduplicationConfig deduplicationConfig = new DefaultEventDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isEmpty(), "The default deduplication key should be empty when event ID is null");
    }

    @Test
    void testGetDeduplicationKeyWithNonNullEventId() {

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig = new DefaultEventDeduplicationConfig(event);
        Optional<String> deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertTrue(deduplicationKey.isPresent());
        assertEquals(eventId.toString(), deduplicationKey.get());
    }

    @Test
    void testGetDeduplicationKeyWithDifferentEvents() {

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);

        DefaultEventDeduplicationConfig deduplicationConfig1 = new DefaultEventDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);

        DefaultEventDeduplicationConfig deduplicationConfig2 = new DefaultEventDeduplicationConfig(event2);
        Optional<String> deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertNotEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Different events should have different deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyWithSameData() {

        UUID eventId = UUID.randomUUID();

        Event event1 = new Event();
        event1.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig1 = new DefaultEventDeduplicationConfig(event1);
        Optional<String> deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        Event event2 = new Event();
        event2.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig2 = new DefaultEventDeduplicationConfig(event2);
        Optional<String> deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertTrue(deduplicationKey1.isPresent());
        assertTrue(deduplicationKey2.isPresent());
        assertEquals(deduplicationKey1.get(), deduplicationKey2.get(), "Events with the same data should have the same deduplication key");
    }
}
