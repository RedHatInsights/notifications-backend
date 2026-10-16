package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventRepositoryTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EventRepository eventRepository;

    @Inject
    EntityManager entityManager;

    @Transactional
    EventType createEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-" + new SecureRandom().nextInt());
        Application app = resourceHelpers.createApp(bundle.getId(), "app-" + new SecureRandom().nextInt());
        return resourceHelpers.createEventType(app.getId(), "event-type-" + new SecureRandom().nextInt());
    }

    @Test
    @Transactional
    void testCreate_PersistsEvent() {
        EventType eventType = createEventType();
        Event event = new Event();
        event.setAccountId("account-id");
        event.setOrgId("test-org-id");
        event.setEventType(eventType);
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setApplicationId(eventType.getApplication().getId());
        event.setApplicationDisplayName(eventType.getApplication().getDisplayName());
        event.setBundleId(eventType.getApplication().getBundle().getId());
        event.setBundleDisplayName(eventType.getApplication().getBundle().getDisplayName());

        Event created = eventRepository.create(event);

        assertNotNull(created.getId());
        Event persisted = entityManager.find(Event.class, created.getId());
        assertEquals("test-org-id", persisted.getOrgId());
    }

    @Test
    @Transactional
    void testUpdateEventDisplayName_UpdatesExistingEvent() {
        EventType eventType = createEventType();
        Event event = resourceHelpers.createEvent(eventType);

        eventRepository.updateEventDisplayName(event.getId(), "a new display name");

        entityManager.clear();
        Event persisted = entityManager.find(Event.class, event.getId());
        assertEquals("a new display name", persisted.getEventTypeDisplayName());
    }
}
