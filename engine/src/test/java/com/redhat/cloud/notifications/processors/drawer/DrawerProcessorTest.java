package com.redhat.cloud.notifications.processors.drawer;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class DrawerProcessorTest {

    @Inject
    DrawerProcessor testee;

    @InjectSpy
    DrawerNotificationRepository drawerNotificationRepository;

    @InjectMock
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    EntityManager entityManager;

    @Inject
    protected ResourceHelpers resourceHelpers;

    @InjectSpy
    NotificationHistoryRepository notificationHistoryRepository;

    @InjectMock
    EngineConfig engineConfig;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    protected InMemorySink<JsonObject> inMemoryToCamelSink;

    @PostConstruct
    void postConstruct() {
        inMemoryToCamelSink = inMemoryConnector.sink(TOCAMEL_CHANNEL);
    }

    @Test
    void shouldNotProcessWhenEndpointsAreNull() {
        testee.process(new Event(), null);
        verify(drawerNotificationRepository, never()).create(any(Event.class), any(String.class));
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        testee.process(new Event(), List.of());
        verify(drawerNotificationRepository, never()).create(any(Event.class), any(String.class));
    }

    @Test
    void shouldCreateTwoDrawerNotifications() {
        User user1 = new User();
        user1.setId("foo");
        user1.setUsername("foo");
        User user2 = new User();
        user1.setId("bar");
        user2.setUsername("bar");

        when(externalRecipientsResolver.recipientUsers(any(), any(), any(), any(), eq(true), any(RecipientsAuthorizationCriterion.class)))
                .thenReturn(Set.of(user1, user2));

        Event createdEvent = createEvent();

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.DRAWER);

        when(engineConfig.isDrawerEnabled()).thenReturn(true);
        testee.process(createdEvent, List.of(endpoint));

        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));

        assertEquals("1 event triggered", createdEvent.getRenderedDrawerNotification());
        Event event = entityManager.createQuery("SELECT e FROM Event e WHERE e.id = :eventId", Event.class).setParameter("eventId", createdEvent.getId()).getSingleResult();
        assertNotNull(event);
        assertEquals(createdEvent.getRenderedDrawerNotification(), event.getRenderedDrawerNotification());

        Message<JsonObject> message;

        // only one event must be send to the connector, because engine is no more fetching users
        await().until(() -> inMemoryToCamelSink.received().size() == 1);
        message = inMemoryToCamelSink.received().get(0);

        assertNotNull(message);
        assertFalse(message.getPayload().isEmpty());

        assertNotNull(message.getPayload().getJsonObject("event_data"));
        CloudEventMetadata cloudEventMetadata = message.getMetadata(CloudEventMetadata.class).get();
        assertNotNull(cloudEventMetadata);
        assertFalse(cloudEventMetadata.getId().isEmpty());
        assertFalse(cloudEventMetadata.getType().isEmpty());

        deleteEvent(createdEvent);
    }

    @Transactional
    Event createEvent() {
        Bundle createdBundle = resourceHelpers.createBundle("test-drawer-engine-event-bundle");
        Application createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-drawer-engine-event-application");
        EventType createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-drawer-engine-event-type");
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
