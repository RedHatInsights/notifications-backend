package com.redhat.cloud.notifications.events.deduplication;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.models.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultEventDeduplicationConfigTest {

    @Test
    void testGetDeduplicationKey() {

        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig = new DefaultEventDeduplicationConfig(event);
        String deduplicationKey = deduplicationConfig.getDeduplicationKey();

        assertNotNull(deduplicationKey);
        assertEquals(eventId.toString(), deduplicationKey);
    }

    @Test
    void testGetDeduplicationKeyWithDifferentEvents() {

        UUID eventId1 = UUID.randomUUID();
        Event event1 = new Event();
        event1.setId(eventId1);

        DefaultEventDeduplicationConfig deduplicationConfig1 = new DefaultEventDeduplicationConfig(event1);
        String deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        UUID eventId2 = UUID.randomUUID();
        Event event2 = new Event();
        event2.setId(eventId2);

        DefaultEventDeduplicationConfig deduplicationConfig2 = new DefaultEventDeduplicationConfig(event2);
        String deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertNotNull(deduplicationKey1);
        assertNotNull(deduplicationKey2);
        assertNotEquals(deduplicationKey1, deduplicationKey2, "Different events should have different deduplication keys");
    }

    @Test
    void testGetDeduplicationKeyWithSameData() {

        UUID eventId = UUID.randomUUID();

        Event event1 = new Event();
        event1.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig1 = new DefaultEventDeduplicationConfig(event1);
        String deduplicationKey1 = deduplicationConfig1.getDeduplicationKey();

        Event event2 = new Event();
        event2.setId(eventId);

        DefaultEventDeduplicationConfig deduplicationConfig2 = new DefaultEventDeduplicationConfig(event2);
        String deduplicationKey2 = deduplicationConfig2.getDeduplicationKey();

        assertNotNull(deduplicationKey1);
        assertNotNull(deduplicationKey2);
        assertEquals(deduplicationKey1, deduplicationKey2, "Events with the same data should have the same deduplication key");
    }
}
