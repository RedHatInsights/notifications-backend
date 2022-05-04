package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventLogCleanerTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void testPostgresStoredProcedure() {
        deleteAllEvents();
        EventType eventType = createEventType();
        createEvent(eventType, now().minus(Duration.ofHours(1L)));
        createEvent(eventType, now().minus(Duration.ofDays(62L)));
        assertEquals(2L, count());
        entityManager.createNativeQuery("CALL cleanEventLog()").executeUpdate();
        assertEquals(1L, count());
    }

    private Integer deleteAllEvents() {
        return entityManager.createQuery("DELETE FROM Event")
                .executeUpdate();
    }

    private EventType createEventType() {
        Bundle bundle = new Bundle();
        bundle.setName("bundle");
        bundle.setDisplayName("Bundle");
        bundle.prePersist();
        entityManager.persist(bundle);

        Application app = new Application();
        app.setBundle(bundle);
        app.setBundleId(bundle.getId());
        app.setName("app");
        app.setDisplayName("Application");
        app.prePersist();
        entityManager.persist(app);

        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        eventType.setName("event-type");
        eventType.setDisplayName("Event type");
        entityManager.persist(eventType);

        return eventType;
    }

    private void createEvent(EventType eventType, LocalDateTime created) {
        Event event = new Event("account-id", eventType, UUID.randomUUID());
        event.setCreated(created);
        entityManager.persist(event);
    }

    private Long count() {
        return entityManager.createQuery("SELECT COUNT(*) FROM Event", Long.class)
                .getSingleResult();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
