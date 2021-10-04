package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.db.EventLogCleaner.EVENT_LOG_CLEANER_DELETE_AFTER_CONF_KEY;
import static com.redhat.cloud.notifications.db.EventLogCleaner.now;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventLogCleanerTest extends DbIsolatedTest {

    /*
     * The Event#created field is automatically set because of the @PrePersist annotation in CreationTimestamped when
     * an Event is persisted using a stateful session. @PrePersist does not work with a stateless session. We need to
     * set Event#created manually to a past date in the tests below, that's why these tests are run using a stateless
     * session.
     */
    @Inject
    Mutiny.StatelessSession statelessSession;

    @Inject
    EventLogCleaner eventLogCleaner;

    @BeforeEach
    void beforeEach() {
        statelessSession.createQuery("DELETE FROM Event")
                .executeUpdate()
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
    }

    @Test
    void testWithDefaultConfiguration() {
        createEventType()
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofHours(1L))))
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofDays(62L))))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
        assertCount(2L);
        eventLogCleaner.clean();
        assertCount(1L);
    }

    @Test
    void testWithCustomConfiguration() {
        System.setProperty(EVENT_LOG_CLEANER_DELETE_AFTER_CONF_KEY, "30m");
        createEventType()
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofHours(1L))))
                .call(eventType -> createEvent(eventType, now().minus(Duration.ofDays(62L))))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
        assertCount(2L);
        eventLogCleaner.clean();
        assertCount(0L);
        System.clearProperty(EVENT_LOG_CLEANER_DELETE_AFTER_CONF_KEY);
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

        return statelessSession.insert(bundle)
                .call(() -> statelessSession.insert(app))
                .call(() -> statelessSession.insert(eventType))
                .replaceWith(eventType);
    }

    private Uni<Void> createEvent(EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setEventType(eventType);
        event.setAccountId("account-id");
        event.setCreated(created);
        return statelessSession.insert(event);
    }

    private void assertCount(long expectedCount) {
        statelessSession.createQuery("SELECT COUNT(*) FROM Event", Long.class)
                .getSingleResult()
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertItem(expectedCount)
                .assertCompleted();
    }
}
