package com.redhat.cloud.notifications.processors.drawer;


import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class DrawerProcessorTest {

    @Inject
    DrawerProcessor testee;

    @InjectSpy
    DrawerNotificationRepository drawerNotificationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @Inject
    EntityManager entityManager;

    @Inject
    StatelessSessionFactory statelessSessionFactory;


    @Inject
    protected ResourceHelpers resourceHelpers;

    @InjectSpy
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void shouldNotProcessWhenEndpointsAreNull() {
        statelessSessionFactory.withSession(statelessSession -> {
            testee.process(new Event(), null);
        });
        verify(drawerNotificationRepository, never()).create(any(Event.class), any(String.class));
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        statelessSessionFactory.withSession(statelessSession -> {
            testee.process(new Event(), List.of());
        });
        verify(drawerNotificationRepository, never()).create(any(Event.class), any(String.class));
    }

    @Test
    void shouldCreateTwoDrawerNotifications() {
        User user1 = new User();
        user1.setUsername("foo");
        User user2 = new User();
        user2.setUsername("bar");

        when(recipientResolver.recipientUsers(any(), any(), any(), eq(false)))
            .thenReturn(Set.of(user1, user2));

        Event createdEvent = createEvent();

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.DRAWER);
        statelessSessionFactory.withSession(statelessSession -> {
            try {
                featureFlipper.setDrawerEnabled(true);
                testee.process(createdEvent, List.of(endpoint));
            } finally {
                featureFlipper.setDrawerEnabled(false);
            }
        });
        verify(drawerNotificationRepository, times(1)).create(any(Event.class), any(String.class));
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));
        List<DrawerNotification> drawerList = entityManager.createQuery("SELECT d FROM DrawerNotification d WHERE d.event.id = :eventId", DrawerNotification.class).setParameter("eventId", createdEvent.getId()).getResultList();
        assertEquals(2, drawerList.size());
        assertEquals("1 event triggered", createdEvent.getRenderedDrawerNotification());
        Event event = entityManager.createQuery("SELECT e FROM Event e WHERE e.id = :eventId", Event.class).setParameter("eventId", createdEvent.getId()).getSingleResult();
        assertNotNull(event);
        assertEquals(createdEvent.getRenderedDrawerNotification(), event.getRenderedDrawerNotification());

        deleteEvent(createdEvent);
    }

    @Transactional
    Event createEvent() {
        Bundle createdBundle = resourceHelpers.createBundle("test-engine-event-repository-bundle");
        Application createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-engine-event-repository-application");
        EventType createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-engine-event-repository-event-type");
        Event createdEvent = new Event();
        createdEvent.setEventType(createdEventType);
        createdEvent.setId(UUID.randomUUID());
        createdEvent.setEventWrapper(new EventWrapperAction(
            new Action.ActionBuilder()
                .withOrgId("123456")
                .withEventType("triggered")
                .withApplication("policies")
                .withBundle("rhel")
                .withTimestamp(LocalDateTime.of(2022, 8, 24, 13, 30, 0, 0))
                .withContext(
                    new Context.ContextBuilder()
                        .withAdditionalProperty("foo", "im foo")
                        .withAdditionalProperty("bar", Map.of("baz", "im baz"))
                        .build()
                )
                .withEvents(List.of(
                    new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                        .withMetadata(new Metadata())
                        .withPayload(new Payload())
                        .build()
                ))
                .build()
        ));

        return resourceHelpers.createEvent(createdEvent);
    }

    @Transactional
    void deleteEvent(Event event) {
        entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", event.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM EventType WHERE id = :uuid").setParameter("uuid", event.getEventType().getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Application WHERE id = :uuid").setParameter("uuid", event.getEventType().getApplicationId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :uuid").setParameter("uuid", event.getEventType().getApplication().getBundleId()).executeUpdate();
        entityManager.createQuery("DELETE FROM DrawerNotification").executeUpdate();
    }

}
