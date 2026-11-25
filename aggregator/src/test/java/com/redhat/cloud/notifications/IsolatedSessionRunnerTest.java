package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.helpers.IsolatedSessionRunner;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class IsolatedSessionRunnerTest {

    @Inject
    IsolatedSessionRunner isolatedSessionRunner;

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers helpers;

    UUID eventId;

    @BeforeEach
    void setUp() {
        helpers.purgeEventAggregations();
        String payload = TestHelpers.generatePayloadContent("sessionOrg", "rhel", "policies", "policyId", "hostId").toString();
        eventId = helpers.addEventEmailAggregation("sessionOrg", "rhel", "policies", LocalDateTime.now(UTC).minusHours(1), payload, false).getId();
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
    }

    @Test
    void theWorkGetsItsOwnSessionAndTheOuterSessionSurvives() {
        Event outerEvent = entityManager.find(Event.class, eventId);
        assertTrue(entityManager.contains(outerEvent));

        Event innerEvent = isolatedSessionRunner.call(() -> {
            Event loadedInside = entityManager.find(Event.class, eventId);
            assertNotSame(outerEvent, loadedInside, "A fresh session loads its own instance");
            assertTrue(entityManager.contains(loadedInside));
            assertFalse(entityManager.contains(outerEvent), "The outer session is not visible from inside");
            return loadedInside;
        });

        assertTrue(entityManager.contains(outerEvent), "The outer session is restored for the caller");
        assertFalse(entityManager.contains(innerEvent), "Everything the inner session loaded is given up with it");
        assertEquals(eventId, innerEvent.getId());
    }

    @Test
    void anExceptionPropagatesAndTheOuterSessionIsStillRestored() {
        Event outerEvent = entityManager.find(Event.class, eventId);

        assertThrows(IllegalStateException.class, () -> isolatedSessionRunner.call(() -> {
            entityManager.find(Event.class, eventId);
            throw new IllegalStateException("simulated failure");
        }));

        assertTrue(entityManager.contains(outerEvent));
    }

    @Test
    void worksWhenNoRequestContextIsActive() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Event event = executor.submit(() -> isolatedSessionRunner.call(() -> entityManager.find(Event.class, eventId))).get();
            assertNotNull(event);
            assertEquals(eventId, event.getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sessionsOfConsecutiveCallsAreIndependent() {
        Event first = isolatedSessionRunner.call(() -> entityManager.find(Event.class, eventId));
        Event second = isolatedSessionRunner.call(() -> entityManager.find(Event.class, eventId));

        assertNotSame(first, second);
    }
}
