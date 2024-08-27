package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.processors.email.EmailProcessor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestHelpers.createPoliciesAction;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static io.restassured.RestAssured.given;
import static java.time.Month.AUGUST;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ReplayResourceTest {

    private static final String ORG_ID_1 = "123";
    private static final String ORG_ID_2 = "456";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    EmailProcessor emailConnectorProcessor;

    private Event event1;
    private Event event2;
    private Event event3;
    private Event event4;
    private Event event5;

    @BeforeEach
    void beforeEach() {

        Bundle bundle = resourceHelpers.createBundle("replay-bundle-" + new SecureRandom().nextInt());
        Application app = resourceHelpers.createApp(bundle.getId(), "replay-app-" + new SecureRandom().nextInt());
        app.setBundle(bundle);
        EventType eventType = resourceHelpers.createEventType(app.getId(), "replay-event-type-" + new SecureRandom().nextInt());
        eventType.setApplication(app);
        Endpoint endpoint = resourceHelpers.createEndpoint(EndpointType.EMAIL_SUBSCRIPTION, null, true, 0);

        LocalDateTime aug24 = LocalDateTime.of(2024, AUGUST, 24, 12, 0);

        String payload = serializeAction(createPoliciesAction("123", bundle.getName(), app.getName(), "host"));

        // OrgId: 123

        when(endpointRepository.getTargetEndpoints(eq(ORG_ID_1), eq(eventType))).thenReturn(List.of(endpoint));

        // The creation date of this event is before the incident time frame.
        event1 = resourceHelpers.createEvent(eventType, ORG_ID_1, LocalDateTime.of(2024, AUGUST, 20, 12, 0), payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, endpoint, event1);

        event2 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.SUCCESS, endpoint, event2);

        event3 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, endpoint, event3);

        event4 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.SUCCESS, endpoint, event4);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, endpoint, event4);

        // OrgId: 456

        when(endpointRepository.getTargetEndpoints(eq(ORG_ID_2), eq(eventType))).thenReturn(List.of(endpoint));

        event5 = resourceHelpers.createEvent(eventType, ORG_ID_2, aug24, payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, endpoint, event5);

        entityManager.clear();
    }

    @Test
    void testReplayAll() {
        given()
                .basePath(API_INTERNAL)
                .when()
                .post("/replay")
                .then()
                .statusCode(204);

        verify(emailConnectorProcessor, never()).process(eq(event1), anyList());
        verify(emailConnectorProcessor, never()).process(eq(event2), anyList());
        verify(emailConnectorProcessor, times(1)).process(eq(event3), anyList());
        verify(emailConnectorProcessor, never()).process(eq(event4), anyList());
        verify(emailConnectorProcessor, times(1)).process(eq(event5), anyList());
    }

    @Test
    void testReplayOrgId123() {
        given()
                .basePath(API_INTERNAL)
                .queryParam("orgId", "123")
                .when()
                .post("/replay")
                .then()
                .statusCode(204);

        verify(emailConnectorProcessor, never()).process(eq(event1), anyList());
        verify(emailConnectorProcessor, never()).process(eq(event2), anyList());
        verify(emailConnectorProcessor, times(1)).process(eq(event3), anyList());
        verify(emailConnectorProcessor, never()).process(eq(event4), anyList());
        verify(emailConnectorProcessor, never()).process(eq(event5), anyList());
    }

    @Transactional
    void createNotificationHistory(NotificationStatus status, Endpoint endpoint, Event event) {
        NotificationHistory notificationHistory = new NotificationHistory(
                UUID.randomUUID(),
                0L,
                true,
                status,
                endpoint,
                LocalDateTime.now(UTC)
        );
        notificationHistory.setEvent(event);
        notificationHistory.setEndpointType(endpoint.getType());
        entityManager.persist(notificationHistory);
    }
}
