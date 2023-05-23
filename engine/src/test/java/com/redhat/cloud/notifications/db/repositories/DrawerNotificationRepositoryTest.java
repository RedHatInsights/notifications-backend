package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerNotification;
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
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


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

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @BeforeEach
    @Transactional
    void insertEventFixtures() {
        createdBundle = resourceHelpers.createBundle("test-engine-event-repository-bundle");
        createdApplication = resourceHelpers.createApp(createdBundle.getId(), "test-engine-event-repository-application");
        createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-engine-event-repository-event-type");
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

    @Test
    @Transactional
    void testSimpleCreateGetCascadeDelete() {
        this.statelessSessionFactory.withSession(session -> {
            DrawerNotification notificationDrawer1 = new DrawerNotification();
            notificationDrawer1.setUserId("user-1");
            notificationDrawer1.setEvent(createdEvent);
            notificationDrawer1.setOrgId(DEFAULT_ORG_ID);
            drawerNotificationsRepository.create(notificationDrawer1);

            DrawerNotification notificationDrawer2 = new DrawerNotification();
            notificationDrawer2.setUserId("user-2");
            notificationDrawer2.setEvent(createdEvent);
            notificationDrawer2.setOrgId(DEFAULT_ORG_ID);
            drawerNotificationsRepository.create(notificationDrawer2);

            List<DrawerNotification> drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
            List<DrawerNotification> drawerNotificationUser3 = getDrawerNotificationsByUserId("user-3");

            assertEquals(1, drawerNotificationUser1.size());
            assertNotNull(drawerNotificationUser1.get(0).getCreated());
            assertEquals(0, drawerNotificationUser3.size());

            entityManager.createQuery("DELETE FROM Event WHERE id = :uuid").setParameter("uuid", createdEvent.getId()).executeUpdate();
            drawerNotificationUser1 = getDrawerNotificationsByUserId("user-1");
            assertEquals(0, drawerNotificationUser1.size());
        });
    }

    public List<DrawerNotification> getDrawerNotificationsByUserId(String userId) {
        String query = "SELECT dn FROM DrawerNotification dn WHERE dn.userId = :userId and dn.orgId = :orgId";
        return entityManager.createQuery(query, DrawerNotification.class)
            .setParameter("userId", userId)
            .setParameter("orgId", DEFAULT_ORG_ID)
            .getResultList();
    }
}
