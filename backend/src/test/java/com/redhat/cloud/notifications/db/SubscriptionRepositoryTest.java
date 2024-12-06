package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionRepositoryTest {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Test
    void testResubscribeAllUsersWithUnknownEventTypeId() {

        EventType eventType = createEventType(true, true, false);
        createUnsubscription(eventType.getId());
        createUnsubscription(eventType.getId());

        assertEquals(2, countUnsubscriptions(eventType.getId()));
        UUID unknownEventTypeId = UUID.randomUUID();
        subscriptionRepository.resubscribeAllUsersIfNeeded(unknownEventTypeId);
        assertEquals(2, countUnsubscriptions(eventType.getId()));
    }

    @Test
    void testResubscribeAllUsersWithNotLockedEventType() {

        EventType eventType = createEventType(false, false, false);
        createUnsubscription(eventType.getId());
        createUnsubscription(eventType.getId());

        assertEquals(2, countUnsubscriptions(eventType.getId()));
        subscriptionRepository.resubscribeAllUsersIfNeeded(eventType.getId());
        assertEquals(2, countUnsubscriptions(eventType.getId()));
    }

    @Test
    void testResubscribeAllUsersWithLockedEventType() {

        EventType eventType = createEventType(true, true, false);
        createUnsubscription(eventType.getId());
        createUnsubscription(eventType.getId());

        assertEquals(2, countUnsubscriptions(eventType.getId()));
        subscriptionRepository.resubscribeAllUsersIfNeeded(eventType.getId());
        assertEquals(0, countUnsubscriptions(eventType.getId()));
    }

    @Transactional
    EventType createEventType(boolean subscribedByDefault, boolean subscriptionLocked, boolean restrictToNamedRecipients) {
        Bundle bundle = resourceHelpers.createBundle("bundle" + randomString(), randomString());
        Application app = resourceHelpers.createApplication(bundle.getId(), "app" + randomString(), randomString());
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type" + randomString());
        eventType.setSubscribedByDefault(subscribedByDefault);
        eventType.setSubscriptionLocked(subscriptionLocked);
        eventType.setRestrictToRecipientsIntegrations(restrictToNamedRecipients);
        return eventType;
    }

    @Transactional
    void createUnsubscription(UUID eventTypeId) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);

        EventTypeEmailSubscription subscription = new EventTypeEmailSubscription();
        subscription.setId(new EventTypeEmailSubscriptionId());
        subscription.setOrgId("not-used");
        subscription.setUserId(UUID.randomUUID().toString());
        subscription.setEventType(eventType);
        subscription.setType(INSTANT);
        subscription.setSubscribed(false);
        entityManager.persist(subscription);
    }

    long countUnsubscriptions(UUID eventTypeId) {
        String hql = "SELECT COUNT(*) FROM EventTypeEmailSubscription " +
                "WHERE eventType.id = :eventTypeId AND NOT subscribed";
        return entityManager.createQuery(hql, Long.class)
                .setParameter("eventTypeId", eventTypeId)
                .getSingleResult();
    }

    private static String randomString() {
        return UUID.randomUUID().toString();
    }
}
