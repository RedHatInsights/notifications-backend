package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class NotificationRepositoryTest {

    @Inject
    EntityManager entityManager;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that the {@link NotificationRepository#countNotificationHistoryElements(UUID, String)} function returns
     * the expected number of elements. For that, fixtures are created all the way until a "notification history"
     * element is created, which then it is expected to be counted.
     */
    @Test
    @Transactional
    public void notificationHistoryElementsCountTest() {
        final String notUsedField = "not-used";
        final String orgId = "test-org-id-count";

        // Create a bundle.
        final Bundle bundle = this.resourceHelpers.createBundle();

        // Create an application.
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        // Create an event type.
        final String eventTypeName = "test-count-event-type-name";
        final String eventTypeDisplayName = "test-count-event-type-display-name";
        final String eventTypeDescription = "test-count-event-type-description";
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), eventTypeName, eventTypeDisplayName, eventTypeDescription);

        // Create an event.
        final String eventDisplayName = "test-count-event-display-name";
        final String eventPayload = "test-count-payload";
        final Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAccountId(notUsedField);
        event.setEventType(eventType);
        event.setPayload(eventPayload);
        event.setCreated(LocalDateTime.now());
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(application.getId());
        event.setApplicationDisplayName(application.getDisplayName());
        event.setEventTypeDisplayName(eventDisplayName);
        event.setOrgId(orgId);

        this.entityManager.persist(event);

        // Create an endpoint.
        final Endpoint endpoint = this.resourceHelpers.createEndpoint(notUsedField, orgId, EndpointType.CAMEL);

        // Create the target notification history that should be counted.
        final NotificationHistory notificationHistory = new NotificationHistory();
        notificationHistory.setEndpoint(endpoint);
        notificationHistory.setCreated(LocalDateTime.now());
        notificationHistory.setInvocationTime(0L);
        notificationHistory.setId(UUID.randomUUID());
        notificationHistory.setEvent(event);
        notificationHistory.setEndpointType(endpoint.getType());
        notificationHistory.setEndpointSubType(endpoint.getSubType());
        notificationHistory.setStatus(NotificationStatus.SUCCESS);

        this.entityManager.persist(notificationHistory);

        // Call the function under test.
        final long count = this.notificationRepository.countNotificationHistoryElements(endpoint.getId(), orgId);

        // The function should return the element we have just created for this test.
        final long expectedCount = 1;
        Assertions.assertEquals(expectedCount, count, "the function under test has returned the wrong count");
    }
}
