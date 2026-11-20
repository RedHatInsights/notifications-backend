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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private EventType createTestEventType(String bundleName, String appName, String eventTypeName) {
        Bundle bundle = resourceHelpers.createBundle(bundleName);
        Application app = resourceHelpers.createApplication(bundle.getId(), appName);
        return resourceHelpers.createEventType(app.getId(), eventTypeName);
    }

    @Transactional
    List<Object[]> getChangeLogRows(UUID eventTypeId) {
        String sql = "SELECT id, org_id, user_id, event_type_id, subscription_type, subscribed, created "
            + "FROM subscription_change_log WHERE event_type_id = :eventTypeId ORDER BY created";
        return entityManager.createNativeQuery(sql)
            .setParameter("eventTypeId", eventTypeId)
            .getResultList();
    }
}
