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
        DrawerNotification notificationDrawer1 = new DrawerNotification();
        notificationDrawer1.setUserId("user-1");
        notificationDrawer1.setEvent(createdEvent);
        notificationDrawer1.setEventId(createdEvent.getId());
        notificationDrawer1.setOrgId(DEFAULT_ORG_ID);
        createDrawerNotification(notificationDrawer1);

        DrawerNotification notificationDrawer2 = new DrawerNotification();
        notificationDrawer2.setUserId("user-2");
        notificationDrawer2.setEvent(createdEvent);
        notificationDrawer2.setEventId(createdEvent.getId());
        notificationDrawer2.setOrgId(DEFAULT_ORG_ID);
        createDrawerNotification(notificationDrawer2);

        List<DrawerNotification> drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
        List<DrawerNotification> drawerNotificationUser3 = getDrawerNotificationsByUserId("user-3");

        assertEquals(1, drawerNotificationUser1.size());
        assertNotNull(drawerNotificationUser1.get(0).getCreated());
        assertEquals(0, drawerNotificationUser3.size());

        entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", createdEvent.getId()).executeUpdate();
        drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
        assertEquals(0, drawerNotificationUser1.size());
    }

    @Test
    @Transactional
    void testThousandsCreations() {
        List<String> users = new ArrayList<>();
        final int LIMIT = 10000;
        for (int i = 0; i < LIMIT; i++) {
            users.add("'user-" + i + "'");
        }
        String usrList = users.stream().collect(Collectors.joining(","));
        List<UUID> uuidList = new ArrayList<>();
        Instant before = Instant.now();
        List<DrawerNotification> drawerNotifications = drawerNotificationsRepository.create(createdEvent, usrList);
        Instant after = Instant.now();
        long duration = Duration.between(before, after).toMillis();
        assertTrue(duration < 1500, String.format("Injection duration should be lower than 1.5 sec but was %s mills", duration));
        Log.infof("data injection ended after %s Millis ", Duration.between(before, after).toMillis());
        assertEquals(LIMIT, drawerNotifications.size());
        assertNotNull(drawerNotifications.get(0).getId());
        uuidList.addAll(drawerNotifications.stream().map(e -> e.getId()).collect(Collectors.toList()));

        Log.info("Try to insert twice same records");
        drawerNotifications = drawerNotificationsRepository.create(createdEvent, usrList);
        assertEquals(LIMIT, drawerNotifications.size());
        List<UUID> newUuidList = drawerNotifications.stream().map(e -> e.getId()).collect(Collectors.toList());
        assertEquals(uuidList, newUuidList, "Uuid list must be the same");
    }

    public List<DrawerNotification> getDrawerNotificationsByUserId(String userId) {
        String query = "SELECT dn FROM DrawerNotification dn WHERE dn.userId = :userId and dn.orgId = :orgId";
        return entityManager.createQuery(query, DrawerNotification.class)
            .setParameter("userId", userId)
            .setParameter("orgId", DEFAULT_ORG_ID)
            .getResultList();
    }
}
