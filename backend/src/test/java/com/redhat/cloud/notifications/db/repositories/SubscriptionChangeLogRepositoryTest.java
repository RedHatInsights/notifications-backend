package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionChangeLogRepositoryTest extends DbIsolatedTest {

    @Inject
    SubscriptionChangeLogRepository subscriptionChangeLogRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Test
    void testLogChangeSubscribe() {
        EventType eventType = createTestEventType("bundle-scl-1", "app-scl-1", "event-scl-1");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, true);

        List<Object[]> rows = getChangeLogRows(eventType.getId());
        assertEquals(1, rows.size());
        assertEquals(true, rows.get(0)[5]);
    }

    @Test
    void testLogChangeUnsubscribe() {
        EventType eventType = createTestEventType("bundle-scl-2", "app-scl-2", "event-scl-2");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, false);

        List<Object[]> rows = getChangeLogRows(eventType.getId());
        assertEquals(1, rows.size());
        assertEquals(false, rows.get(0)[5]);
    }

    @Test
    void testMultipleTogglesProduceMultipleRows() {
        EventType eventType = createTestEventType("bundle-scl-3", "app-scl-3", "event-scl-3");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, true);
        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, false);
        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, true);

        List<Object[]> rows = getChangeLogRows(eventType.getId());
        assertEquals(3, rows.size());
    }

    @Test
    void testCountUnsubscribesByEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-scl-4");
        Application app = resourceHelpers.createApplication(bundle.getId(), "app-scl-4");
        EventType eventType1 = resourceHelpers.createEventType(app.getId(), "event-scl-4a");
        EventType eventType2 = resourceHelpers.createEventType(app.getId(), "event-scl-4b");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType1.getId(), SubscriptionType.INSTANT, false);
        subscriptionChangeLogRepository.logChange("org-2", "user-2", eventType1.getId(), SubscriptionType.INSTANT, false);
        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType1.getId(), SubscriptionType.INSTANT, true);
        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType2.getId(), SubscriptionType.DAILY, false);

        OffsetDateTime yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        List<Object[]> stats = subscriptionChangeLogRepository.countUnsubscribesByEventType(yesterday);

        boolean foundEventType1 = false;
        boolean foundEventType2 = false;
        for (Object[] row : stats) {
            UUID id = (UUID) row[0];
            long count = ((Number) row[2]).longValue();
            if (id.equals(eventType1.getId())) {
                assertEquals(2, count, "eventType1 should have 2 unsubscribes (subscribed=true not counted)");
                foundEventType1 = true;
            } else if (id.equals(eventType2.getId())) {
                assertEquals(1, count, "eventType2 should have 1 unsubscribe");
                foundEventType2 = true;
            }
        }
        assertTrue(foundEventType1, "eventType1 should appear in stats");
        assertTrue(foundEventType2, "eventType2 should appear in stats");
    }

    @Test
    void testCountUnsubscribesByEventTypeRespectsSinceCutoff() {
        EventType eventType = createTestEventType("bundle-scl-5", "app-scl-5", "event-scl-5");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, false);

        OffsetDateTime tomorrow = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        List<Object[]> stats = subscriptionChangeLogRepository.countUnsubscribesByEventType(tomorrow);

        boolean found = stats.stream().anyMatch(row -> ((UUID) row[0]).equals(eventType.getId()));
        assertFalse(found, "Event type should not appear in stats when since date is in the future");
    }

    private EventType createTestEventType(String bundleName, String appName, String eventTypeName) {
        Bundle bundle = resourceHelpers.createBundle(bundleName);
        Application app = resourceHelpers.createApplication(bundle.getId(), appName);
        return resourceHelpers.createEventType(app.getId(), eventTypeName);
    }

    @Transactional
    List<Object[]> getChangeLogRows(UUID eventTypeId) {
        String sql = "SELECT id, org_id, user_id, event_type_id, subscription_type, subscribed, changed_at "
            + "FROM subscription_change_log WHERE event_type_id = :eventTypeId ORDER BY changed_at";
        return entityManager.createNativeQuery(sql)
            .setParameter("eventTypeId", eventTypeId)
            .getResultList();
    }
}
