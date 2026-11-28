package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionChangeLogRepositoryTest extends DbIsolatedTest {

    @Inject
    SubscriptionChangeLogRepository subscriptionChangeLogRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @InjectMock
    BackendConfig backendConfig;

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

    @Test
    void testUpdateSubscriptionCreatesChangeLogEntry() {
        Mockito.when(backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        EventType eventType = createTestEventType("bundle-scl-int-1", "app-scl-int-1", "event-scl-int-1");
        Map<Severity, Boolean> severities = buildAllSeveritiesMap(true);

        subscriptionRepository.updateSubscription("org-int", "user-int", eventType.getId(), SubscriptionType.INSTANT, true, severities);

        List<Object[]> rows = getChangeLogRows(eventType.getId());
        assertEquals(1, rows.size(), "updateSubscription should create a change log entry");
        assertEquals("org-int", rows.get(0)[1]);
        assertEquals("user-int", rows.get(0)[2]);
        assertEquals("INSTANT", rows.get(0)[4]);
        assertEquals(true, rows.get(0)[5]);
    }

    @Test
    void testUpdateSubscriptionToggleProducesMultipleLogEntries() {
        Mockito.when(backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        EventType eventType = createTestEventType("bundle-scl-int-2", "app-scl-int-2", "event-scl-int-2");
        Map<Severity, Boolean> severitiesOn = buildAllSeveritiesMap(true);
        Map<Severity, Boolean> severitiesOff = buildAllSeveritiesMap(false);

        subscriptionRepository.updateSubscription("org-int", "user-int", eventType.getId(), SubscriptionType.INSTANT, true, severitiesOn);
        subscriptionRepository.updateSubscription("org-int", "user-int", eventType.getId(), SubscriptionType.INSTANT, false, severitiesOff);

        List<Object[]> rows = getChangeLogRows(eventType.getId());
        assertEquals(2, rows.size(), "Each updateSubscription call should produce a change log entry");
        assertEquals(true, rows.get(0)[5]);
        assertEquals(false, rows.get(1)[5]);
    }

    private Map<Severity, Boolean> buildAllSeveritiesMap(boolean subscribed) {
        Map<Severity, Boolean> map = new HashMap<>();
        for (Severity severity : Severity.values()) {
            map.put(severity, subscribed);
        }
        return map;
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
