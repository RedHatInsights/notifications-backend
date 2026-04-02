package com.redhat.cloud.notifications.processors.drawer;

import com.redhat.cloud.notifications.Severity;
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
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
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

import static com.redhat.cloud.notifications.processors.ConnectorSender.TOCAMEL_CHANNEL;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
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

    @InjectSpy
    TemplateService templateService;

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
    void shouldNotProcessWhenIncludeInDrawerIsFalse() {
        Event createdEvent = createEvent(false);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.DRAWER);

        when(engineConfig.isDrawerEnabled(anyString())).thenReturn(true);
        testee.process(createdEvent, List.of(endpoint));

        verify(drawerNotificationRepository, never()).create(any(Event.class), any(String.class));
        verify(templateService, never()).renderTemplateWithCustomDataMap(any(TemplateDefinition.class), anyMap());

        deleteEvent(createdEvent);
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

        Event createdEvent = createEvent(true);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.DRAWER);

        when(engineConfig.isDrawerEnabled(anyString())).thenReturn(true);
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
    Event createEvent(boolean includedInDrawer) {
        Bundle createdBundle = resourceHelpers.createBundle("test-drawer-engine-event-bundle");
        Application createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-drawer-engine-event-application");
        EventType createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-drawer-engine-event-type");
        createdEventType.setIncludedInDrawer(includedInDrawer);
        Event createdEvent = new Event();
        createdEvent.setEventType(createdEventType);
        createdEvent.setEventWrapper(new EventWrapperAction(
            new Action.ActionBuilder()
                .withOrgId("123456")
                .withEventType("triggered")
                .withApplication("policies")
                .withBundle("rhel")
                .withTimestamp(LocalDateTime.of(2022, 8, 24, 13, 30, 0, 0))
                .withSeverity(Severity.CRITICAL.name())
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

    @Test
    void shouldProcessOnlyForOrgWithDrawerEnabled() {
        final String ORG_WITH_DRAWER_ENABLED = "org-with-drawer-enabled";
        final String ORG_WITH_DRAWER_DISABLED = "org-with-drawer-disabled";

        when(engineConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_ENABLED))).thenReturn(true);
        when(engineConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_DISABLED))).thenReturn(false);

        User user = new User();
        user.setId("test-user");
        user.setUsername("test-user");

        when(externalRecipientsResolver.recipientUsers(any(), any(), any(), any(), eq(true), any(RecipientsAuthorizationCriterion.class)))
            .thenReturn(Set.of(user));

        // Create schema once (bundle, app, eventType) and two events with different orgIds
        EventType eventType = createSchemaForOrgTest();
        Event eventWithDrawer = createEventWithOrgId(eventType, ORG_WITH_DRAWER_ENABLED);
        Event eventWithoutDrawer = createEventWithOrgId(eventType, ORG_WITH_DRAWER_DISABLED);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.DRAWER);

        testee.process(eventWithDrawer, List.of(endpoint));

        // Verify notification history was created for org-with-drawer-enabled
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));

        testee.process(eventWithoutDrawer, List.of(endpoint));

        // Verify NO additional notification history created (still 1 total from first call)
        verify(notificationHistoryRepository, times(1)).createNotificationHistory(any(NotificationHistory.class));

        // Cleanup
        deleteEventsAndSchema(List.of(eventWithDrawer, eventWithoutDrawer), eventType);
    }

    @Transactional
    EventType createSchemaForOrgTest() {
        Bundle bundle = resourceHelpers.createBundle("test-drawer-org-specific-bundle");
        Application app = resourceHelpers.createApp(bundle.getId(), "test-drawer-org-specific-application");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "test-drawer-org-specific-event-type");
        eventType.setIncludedInDrawer(true);
        return eventType;
    }

    @Transactional
    Event createEventWithOrgId(EventType eventType, String orgId) {
        Event event = new Event();
        event.setEventType(eventType);
        event.setOrgId(orgId);
        event.setEventWrapper(new EventWrapperAction(
            new Action.ActionBuilder()
                .withOrgId(orgId)
                .withEventType("triggered")
                .withApplication("policies")
                .withBundle("rhel")
                .withTimestamp(LocalDateTime.of(2022, 8, 24, 13, 30, 0, 0))
                .withSeverity(Severity.CRITICAL.name())
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
        return resourceHelpers.createEvent(event);
    }

    @Transactional
    void deleteEventsAndSchema(List<Event> events, EventType eventType) {
        entityManager.createQuery("DELETE FROM Event WHERE id IN :uuids")
            .setParameter("uuids", events.stream().map(Event::getId).toList())
            .executeUpdate();
        entityManager.createQuery("DELETE FROM EventType WHERE id = :uuid")
            .setParameter("uuid", eventType.getId())
            .executeUpdate();
        entityManager.createQuery("DELETE FROM Application WHERE id = :uuid")
            .setParameter("uuid", eventType.getApplicationId())
            .executeUpdate();
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :uuid")
            .setParameter("uuid", eventType.getApplication().getBundleId())
            .executeUpdate();
        entityManager.createQuery("DELETE FROM DrawerNotification").executeUpdate();
    }

}
