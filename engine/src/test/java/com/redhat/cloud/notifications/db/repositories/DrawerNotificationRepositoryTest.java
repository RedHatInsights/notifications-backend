package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerNotification;
import com.redhat.cloud.notifications.models.DrawerNotificationId;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DrawerNotificationRepositoryTest {

    private Bundle createdBundle;
    private Application createdApplication;
    private EventType createdEventType;
    private final List<Event> createdEvents = new ArrayList<>(5);

    private Event event = new Event();

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Inject
    EntityManager entityManager;

    @Inject
    DrawerNotificationRepository drawerNotificationsRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    /**
     * Inserts five event fixtures in the database. The fixtures then get
     * their "created at" timestamp modified by removing days from their dates.
     * The first one will have "today - 1 days" as the creation date, the
     * second one will be "today - 2 days" etc.
     */
    @BeforeEach
    @Transactional
    void insertEventFixtures() {
        createdBundle = resourceHelpers.createBundle("test-engine-event-repository-bundle");
        createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-engine-event-repository-application");
        createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-engine-event-repository-event-type");

        // Create on event which will be used in the tests.
        event.setId(UUID.randomUUID());
        event.setAccountId("account-id");
        event.setOrgId(DEFAULT_ORG_ID);
        event.setEventType(createdEventType);
        event.setEventTypeDisplayName(createdEventType.getDisplayName());
        event.setApplicationId(createdApplication.getId());
        event.setApplicationDisplayName(createdApplication.getDisplayName());
        event.setBundleId(createdBundle.getId());
        event.setBundleDisplayName(createdBundle.getDisplayName());
        event.setCreated(LocalDateTime.now(ZoneOffset.UTC));

        this.entityManager.persist(event);
    }

    /**
     * Removes the created fixtures in the database.
     */
    @AfterEach
    @Transactional
    void removeFixtures() {
        entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", event.getId());
        entityManager.createQuery("DELETE FROM EventType WHERE id = :uuid").setParameter("uuid", createdEventType.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Application WHERE id = :uuid").setParameter("uuid", createdApplication.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM Bundle WHERE id = :uuid").setParameter("uuid", createdBundle.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM DrawerNotification").executeUpdate();
    }

    @Test
    @Transactional
    void testSimpleCreateGetCascadeDelete() {
        this.statelessSessionFactory.withSession(session -> {
            DrawerNotification notificationDrawer1 = new DrawerNotification(new DrawerNotificationId());
            notificationDrawer1.setUserId("user-1");
            notificationDrawer1.setEvent(event);
            notificationDrawer1.setOrgId(DEFAULT_ORG_ID);
            drawerNotificationsRepository.create(notificationDrawer1);

            DrawerNotification notificationDrawer2 = new DrawerNotification(new DrawerNotificationId());
            notificationDrawer2.setUserId("user-2");
            notificationDrawer2.setEvent(event);
            notificationDrawer2.setOrgId(DEFAULT_ORG_ID);
            drawerNotificationsRepository.create(notificationDrawer2);

            List<DrawerNotification> drawerNotificationUser1 = drawerNotificationsRepository.getDrawerNotificationsByUserId("user-1");
            List<DrawerNotification> drawerNotificationUser3 = drawerNotificationsRepository.getDrawerNotificationsByUserId("user-3");

            assertEquals(1, drawerNotificationUser1.size());
            assertNotNull(drawerNotificationUser1.get(0).getCreated());
            assertEquals(0, drawerNotificationUser3.size());

            entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", event.getId()).executeUpdate();
            drawerNotificationUser1 = drawerNotificationsRepository.getDrawerNotificationsByUserId("user-1");
            assertEquals(0, drawerNotificationUser1.size());
        });
    }
}
