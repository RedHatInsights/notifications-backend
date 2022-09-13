package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationHistoryRepositoryTest {

    @Inject
    NotificationHistoryRepository repository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Test
    void testCreateHistoryWithExistingEndpoint() {
        Map<String, Object> details = Map.of("alpha", "bravo", "charlie", Map.of("delta", "echo"));
        NotificationHistory history = initData(123L, FALSE, WEBHOOK, null, details);

        statelessSessionFactory.withSession(statelessSession -> {
            repository.createNotificationHistory(history);
        });

        NotificationHistory persistedHistory = entityManager.find(NotificationHistory.class, history.getId());
        assertEquals(history.getId(), persistedHistory.getId());
        assertEquals(history.getInvocationTime(), persistedHistory.getInvocationTime());
        assertEquals(history.isInvocationResult(), persistedHistory.isInvocationResult());
        assertEquals(history.getEvent(), persistedHistory.getEvent());
        assertEquals(history.getEndpoint(), persistedHistory.getEndpoint());
        assertEquals(history.getEndpointType(), persistedHistory.getEndpointType());
        assertEquals(history.getEndpointSubType(), persistedHistory.getEndpointSubType());
        assertEquals(history.getDetails(), persistedHistory.getDetails());
    }

    @Test
    void testCreateHistoryWithDeletedEndpoint() {
        NotificationHistory history = initData(456L, TRUE, CAMEL, "slack", null);
        deleteEndpoint(history.getEndpoint().getId());

        statelessSessionFactory.withSession(statelessSession -> {
            repository.createNotificationHistory(history);
        });

        NotificationHistory persistedHistory = entityManager.find(NotificationHistory.class, history.getId());
        assertEquals(history.getId(), persistedHistory.getId());
        assertEquals(history.getInvocationTime(), persistedHistory.getInvocationTime());
        assertEquals(history.isInvocationResult(), persistedHistory.isInvocationResult());
        assertEquals(history.getEvent(), persistedHistory.getEvent());
        assertNull(persistedHistory.getEndpoint());
        assertEquals(history.getEndpointType(), persistedHistory.getEndpointType());
        assertEquals(history.getEndpointSubType(), persistedHistory.getEndpointSubType());
        assertEquals(history.getDetails(), persistedHistory.getDetails());
    }

    @Transactional
    NotificationHistory initData(Long invocationTime, Boolean invocationResult, EndpointType endpointType,
            String endpointSubType, Map<String, Object> details) {
        Bundle bundle = resourceHelpers.createBundle("bundle-" + new SecureRandom().nextInt());
        Application app = resourceHelpers.createApp(bundle.getId(), "app-" + new SecureRandom().nextInt());
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-" + new SecureRandom().nextInt());
        Event event = resourceHelpers.createEvent(eventType);
        Endpoint endpoint = resourceHelpers.createEndpoint(endpointType, endpointSubType, true, 0);
        return buildNotificationHistory(invocationTime, invocationResult, event, endpoint, details);
    }

    @Transactional
    void deleteEndpoint(UUID endpointId) {
        entityManager.createQuery("DELETE FROM Endpoint WHERE id = :id")
                .setParameter("id", endpointId)
                .executeUpdate();
    }

    private static NotificationHistory buildNotificationHistory(Long invocationTime, Boolean invocationResult,
            Event event, Endpoint endpoint, Map<String, Object> details) {
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setInvocationTime(invocationTime);
        history.setInvocationResult(invocationResult);
        history.setEvent(event);
        history.setEndpoint(endpoint);
        history.setEndpointType(endpoint.getType());
        history.setEndpointSubType(endpoint.getSubType());
        history.setDetails(details);
        return history;
    }
}
