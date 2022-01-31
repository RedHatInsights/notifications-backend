package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventLogCleanerTest {

    /*
     * The Event#created field is automatically set because of the @PrePersist annotation in CreationTimestamped when
     * an Event is persisted using a stateful session. @PrePersist does not work with a stateless session. We need to
     * set Event#created manually to a past date in the tests below, that's why these tests are run using a stateless
     * session.
     */
    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    void testPostgresStoredProcedure() {
        sessionFactory.withStatelessSession(statelessSession -> deleteAllEvents()
                .chain(() -> createEventType())
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofHours(1L))))
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofDays(62L))))
                .chain(() -> count())
                .invoke(count -> assertEquals(2L, count))
                .chain(() -> statelessSession.createNativeQuery("CALL cleanEventLog()").executeUpdate())
                .chain(() -> count())
                .invoke(count -> assertEquals(1L, count))
        ).await().indefinitely();
    }

    private Uni<Integer> deleteAllEvents() {
        return sessionFactory.withStatelessSession(statelessSession ->
                statelessSession.createQuery("DELETE FROM Event")
                    .executeUpdate()
        );
    }

    private Uni<EventType> createEventType() {
        Bundle bundle = new Bundle();
        bundle.setName("bundle");
        bundle.setDisplayName("Bundle");
        bundle.prePersist();

        Application app = new Application();
        app.setBundle(bundle);
        app.setName("app");
        app.setDisplayName("Application");
        app.prePersist();

        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setName("event-type");
        eventType.setDisplayName("Event type");

        return sessionFactory.withStatelessSession(statelessSession ->
                statelessSession.insert(bundle)
                        .call(() -> statelessSession.insert(app))
                        .call(() -> statelessSession.insert(eventType))
                        .replaceWith(eventType)
        );
    }

    private Uni<Void> createEvent(EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setEventType(eventType);
        event.setAccountId("account-id");
        event.setCreated(created);
        return sessionFactory.withStatelessSession(statelessSession -> statelessSession.insert(event));
    }

    private Uni<Long> count() {
        return sessionFactory.withStatelessSession(statelessSession ->
                statelessSession.createQuery("SELECT COUNT(*) FROM Event", Long.class)
                        .getSingleResult()
        );
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
