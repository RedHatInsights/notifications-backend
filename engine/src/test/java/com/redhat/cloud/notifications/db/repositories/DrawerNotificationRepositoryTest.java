package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DrawerNotificationRepositoryTest {

    private Bundle createdBundle;
    private Application createdApplication;
    private EventType createdEventType;
    private Event createdEvent = new Event();

    @Inject
    EntityManager entityManager;

    @Inject
    DrawerNotificationRepository drawerNotificationsRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @BeforeEach
    @Transactional
    void insertEventFixtures() {
        createdBundle = resourceHelpers.createBundle("test-drawer-engine-event-repository-bundle");
        createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-drawer-engine-event-repository-application");
        createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-drawer-engine-event-repository-event-type");
        createdEvent = resourceHelpers.createEvent(createdEventType);
    }

    @AfterEach
    @Transactional
    void removeFixtures() {
        entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", createdEvent.getId());
        entityManager.createQuery("DELETE FROM EventType WHERE id = :uuid").setParameter("uuid", createdEventType.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Application WHERE id = :uuid").setParameter("uuid", createdApplication.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :uuid").setParameter("uuid", createdBundle.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM DrawerNotification").executeUpdate();
    }

    private void createDrawerNotification(DrawerNotification drawerNotification) {
        entityManager.persist(drawerNotification);
    }

    @Test
    @Transactional
    void testSimpleCreateGetCascadeDelete() {
        Event createdEventFromPersistenceContext = entityManager.find(Event.class, createdEvent.getId());
        DrawerNotification notificationDrawer1 = new DrawerNotification(DEFAULT_ORG_ID, "user-1", createdEventFromPersistenceContext);
        createDrawerNotification(notificationDrawer1);

        DrawerNotification notificationDrawer2 = new DrawerNotification(DEFAULT_ORG_ID, "user-2", createdEventFromPersistenceContext);
        createDrawerNotification(notificationDrawer2);

        List<DrawerNotification> drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
        List<DrawerNotification> drawerNotificationUser3 = getDrawerNotificationsByUserId("user-3");

        assertEquals(1, drawerNotificationUser1.size());
        assertNotNull(drawerNotificationUser1.get(0).getCreated());
        assertEquals(0, drawerNotificationUser3.size());

        entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", createdEventFromPersistenceContext.getId()).executeUpdate();
        drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
        assertEquals(0, drawerNotificationUser1.size());
    }

    @Test
    @Transactional
    void testThousandsCreations() {
        List<String> users = new ArrayList<>();
        Event thousandsDrawerNotificationsEvent = resourceHelpers.createEvent(createdEventType);
        final int LIMIT = 10000;
        for (int i = 0; i < LIMIT; i++) {
            users.add("user-" + i);
        }

        String usrList = users.stream().collect(Collectors.joining(","));
        Instant before = Instant.now();
        drawerNotificationsRepository.create(thousandsDrawerNotificationsEvent, usrList);
        Instant after = Instant.now();
        long duration = Duration.between(before, after).toMillis();
        assertTrue(duration < 2000, String.format("Injection duration should be lower than 2 sec but was %s mills", duration));
        Log.infof("data injection ended after %s Millis ", Duration.between(before, after).toMillis());

        List<DrawerNotification> createdNotifications =  getDrawerNotificationsByEventId(thousandsDrawerNotificationsEvent.getId());
        assertEquals(LIMIT, createdNotifications.size());

        Log.info("Try to insert twice same records");
        drawerNotificationsRepository.create(thousandsDrawerNotificationsEvent, usrList);
        createdNotifications =  getDrawerNotificationsByEventId(thousandsDrawerNotificationsEvent.getId());
        assertEquals(LIMIT, createdNotifications.size());
    }

    public List<DrawerNotification> getDrawerNotificationsByUserId(String userId) {
        String query = "SELECT dn FROM DrawerNotification dn WHERE dn.id.userId = :userId and dn.id.orgId = :orgId";
        return entityManager.createQuery(query, DrawerNotification.class)
            .setParameter("userId", userId)
            .setParameter("orgId", DEFAULT_ORG_ID)
            .getResultList();
    }

    public List<DrawerNotification> getDrawerNotificationsByEventId(UUID eventId) {
        String query = "SELECT dn FROM DrawerNotification dn WHERE dn.id.eventId = :eventId";
        return entityManager.createQuery(query, DrawerNotification.class)
            .setParameter("eventId", eventId)
            .getResultList();
    }
}
