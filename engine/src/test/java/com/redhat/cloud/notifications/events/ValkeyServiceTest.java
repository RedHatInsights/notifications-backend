package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class ValkeyServiceTest {

    @Inject
    ValkeyService valkeyService;

    @Test
    void testAddNewEntries() {
        UUID eventTypeId1 = UUID.randomUUID();
        String dedupKey1 = "dedup-key-new-" + UUID.randomUUID();
        LocalDateTime deleteAfter = LocalDateTime.now(ZoneOffset.UTC).plusDays(7);

        // Insert first event
        assertTrue(valkeyService.isNewEvent(eventTypeId1, dedupKey1, deleteAfter));

        // Insert entry with different event type ID
        UUID eventTypeId2 = UUID.randomUUID();
        assertTrue(valkeyService.isNewEvent(eventTypeId2, dedupKey1, deleteAfter));

        // Insert entry with different deduplication key
        String dedupKey2 = "dedup-key-new-" + UUID.randomUUID();
        assertTrue(valkeyService.isNewEvent(eventTypeId1, dedupKey2, deleteAfter));

        // Deduplication required both event type and dedup key to match
        assertTrue(valkeyService.isNewEvent(eventTypeId2, dedupKey2, deleteAfter));

        // Events which should have already expired will also return successfully
        LocalDateTime expiredDeleteAfter = LocalDateTime.of(2023, 5, 11, 15, 40, 21);
        String dedupKey3 = "dedup-key-new-" + UUID.randomUUID();
        assertTrue(valkeyService.isNewEvent(eventTypeId1, dedupKey3, expiredDeleteAfter));
    }

    @Test
    void testAddDuplicateEntries() {
        // Insert initial event
        UUID eventTypeId = UUID.randomUUID();
        String dedupKey = "dedup-key-duplicates-" + UUID.randomUUID();
        LocalDateTime deleteAfter = LocalDateTime.now(ZoneOffset.UTC).plusDays(7);
        assertTrue(valkeyService.isNewEvent(eventTypeId, dedupKey, deleteAfter));

        // Attempt to reinsert the same event
        assertFalse(valkeyService.isNewEvent(eventTypeId, dedupKey, deleteAfter));

        // Changing the expiry date does not affect duplicate detection
        LocalDateTime deleteAfter2 = deleteAfter.plusDays(10);
        assertFalse(valkeyService.isNewEvent(eventTypeId, dedupKey, deleteAfter2));
    }

    @Test
    void testEntryExpiry() throws InterruptedException {
        UUID eventTypeId = UUID.randomUUID();
        String deduplicationKey = "dedup-expiry-key-" + UUID.randomUUID();

        // Key will expire in 3 seconds
        LocalDateTime deleteAfter = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(3);
        valkeyService.isNewEvent(eventTypeId, deduplicationKey, deleteAfter);

        // Wait 5 seconds and attempt to insert key that should have expired
        Thread.sleep(Duration.ofSeconds(5));
        assertTrue(valkeyService.isNewEvent(eventTypeId, deduplicationKey, deleteAfter));
    }
}
