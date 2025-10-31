package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
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
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatProcessor;
import com.redhat.cloud.notifications.processors.camel.slack.SlackProcessor;
import com.redhat.cloud.notifications.processors.camel.teams.TeamsProcessor;
import com.redhat.cloud.notifications.processors.email.EmailProcessor;
import com.redhat.cloud.notifications.routers.replay.EventsReplayRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestHelpers.createPoliciesAction;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.Month.SEPTEMBER;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMock
    GoogleChatProcessor googleChatProcessor;

    @InjectMock
    SlackProcessor slackProcessor;

    @InjectMock
    TeamsProcessor teamsProcessor;

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
        Endpoint emailEndpoint = resourceHelpers.createEndpoint(EndpointType.EMAIL_SUBSCRIPTION, null, true, 0);

        Endpoint slackEndpoint = resourceHelpers.createEndpoint(EndpointType.CAMEL, SLACK_ENDPOINT_SUBTYPE, true, 0);

        Endpoint gchatEndpoint = resourceHelpers.createEndpoint(EndpointType.CAMEL, GOOGLE_CHAT_ENDPOINT_SUBTYPE, true, 0);

        Endpoint teamsEndpoint = resourceHelpers.createEndpoint(EndpointType.CAMEL, TEAMS_ENDPOINT_SUBTYPE, true, 0);

        LocalDateTime aug24 = LocalDateTime.of(2024, SEPTEMBER, 2, 10, 0);

        String payload = serializeAction(createPoliciesAction("123", bundle.getName(), app.getName(), "host"));

        // OrgId: 123

        when(endpointRepository.getTargetEndpointsWithoutUsingBgs(eq(ORG_ID_1), eq(eventType))).thenReturn(List.of(emailEndpoint, gchatEndpoint, slackEndpoint, teamsEndpoint));

        // The creation date of this event is before the incident time frame.
        event1 = resourceHelpers.createEvent(eventType, ORG_ID_1, LocalDateTime.of(2024, SEPTEMBER, 1, 10, 0), payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, emailEndpoint, event1);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, slackEndpoint, event1);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, gchatEndpoint, event1);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, teamsEndpoint, event1);

        event2 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.SUCCESS, emailEndpoint, event2);
        createNotificationHistory(NotificationStatus.SUCCESS, slackEndpoint, event2);
        createNotificationHistory(NotificationStatus.SUCCESS, gchatEndpoint, event2);
        createNotificationHistory(NotificationStatus.SUCCESS, teamsEndpoint, event2);

        event3 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, emailEndpoint, event3);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, slackEndpoint, event3);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, gchatEndpoint, event3);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, teamsEndpoint, event3);

        event4 = resourceHelpers.createEvent(eventType, ORG_ID_1, aug24, payload);
        createNotificationHistory(NotificationStatus.SUCCESS, emailEndpoint, event4);
        createNotificationHistory(NotificationStatus.SUCCESS, slackEndpoint, event4);
        createNotificationHistory(NotificationStatus.SUCCESS, gchatEndpoint, event4);
        createNotificationHistory(NotificationStatus.SUCCESS, teamsEndpoint, event4);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, emailEndpoint, event4);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, slackEndpoint, event4);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, gchatEndpoint, event4);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, teamsEndpoint, event4);

        // OrgId: 456

        when(endpointRepository.getTargetEndpointsWithoutUsingBgs(eq(ORG_ID_2), eq(eventType))).thenReturn(List.of(emailEndpoint, gchatEndpoint, slackEndpoint, teamsEndpoint));

        event5 = resourceHelpers.createEvent(eventType, ORG_ID_2, aug24, payload);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, emailEndpoint, event5);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, slackEndpoint, event5);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, gchatEndpoint, event5);
        createNotificationHistory(NotificationStatus.FAILED_EXTERNAL, teamsEndpoint, event5);

        entityManager.clear();
    }

    @ParameterizedTest
    @MethodSource("endpointTypes")
    void testReplayAll(EndpointType endpointType, String endpointSubType) {
        EventsReplayRequest replayRequest = getEventsReplayRequest(endpointType, endpointSubType);
        given()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(replayRequest))
                .when()
                .post("/replay")
                .then()
                .statusCode(204);

        if (EndpointType.EMAIL_SUBSCRIPTION == endpointType) {
            verify(emailConnectorProcessor, never()).process(eq(event1), any());
            verify(emailConnectorProcessor, never()).process(eq(event2), any());
            verify(emailConnectorProcessor, times(1)).process(eq(event3), any());
            verify(emailConnectorProcessor, never()).process(eq(event4), any());
            verify(emailConnectorProcessor, times(1)).process(eq(event5), any());
            verify(googleChatProcessor, never()).process(any(), any());
            verify(slackProcessor, never()).process(any(), any());
            verify(teamsProcessor, never()).process(any(), any());
        } else {
            verify(emailConnectorProcessor, never()).process(any(), any());
        }
        if (EndpointType.CAMEL == endpointType) {
            checkProcessorInteractions(googleChatProcessor, GOOGLE_CHAT_ENDPOINT_SUBTYPE, endpointSubType);
            checkProcessorInteractions(slackProcessor, SLACK_ENDPOINT_SUBTYPE, endpointSubType);
            checkProcessorInteractions(teamsProcessor, TEAMS_ENDPOINT_SUBTYPE, endpointSubType);
        }
    }

    private void checkProcessorInteractions(CamelProcessor camelProcessor, String endpointSubType, String endpointSubTypeParam) {
        if (endpointSubType.equals(endpointSubTypeParam)) {
            verify(camelProcessor, never()).process(eq(event1), any());
            verify(camelProcessor, never()).process(eq(event2), any());
            verify(camelProcessor, times(1)).process(eq(event3), any());
            verify(camelProcessor, never()).process(eq(event4), any());
            verify(camelProcessor, times(1)).process(eq(event5), any());
        } else {
            verify(camelProcessor, never()).process(any(), any());
        }
    }

    @Test
    void testReplayOrgId123() {
        EventsReplayRequest replayRequest = getEventsReplayRequest();
        replayRequest.orgId = "123";

        given()
                .basePath(API_INTERNAL)
                .contentType(JSON)
                .body(Json.encode(replayRequest))
                .when()
                .post("/replay")
                .then()
                .statusCode(204);

        verify(emailConnectorProcessor, never()).process(eq(event1), any());
        verify(emailConnectorProcessor, never()).process(eq(event2), any());
        verify(emailConnectorProcessor, times(1)).process(eq(event3), any());
        verify(emailConnectorProcessor, never()).process(eq(event4), any());
        verify(emailConnectorProcessor, never()).process(eq(event5), any());
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

    private static @NotNull EventsReplayRequest getEventsReplayRequest(EndpointType endpointType, String endpointSubType) {
        EventsReplayRequest replayRequest = new EventsReplayRequest();
        replayRequest.startDate = LocalDateTime.of(2024, SEPTEMBER, 2, 8, 0);
        replayRequest.endDate = LocalDateTime.of(2024, SEPTEMBER, 2, 11, 0);
        replayRequest.endpointType = endpointType;
        replayRequest.endpointSubType = endpointSubType;
        return replayRequest;
    }

    private static @NotNull EventsReplayRequest getEventsReplayRequest() {
        return getEventsReplayRequest(EndpointType.EMAIL_SUBSCRIPTION, null);
    }

    private static Stream<Arguments> endpointTypes() {
        return Stream.of(
            Arguments.of(EndpointType.EMAIL_SUBSCRIPTION, null),
            Arguments.of(EndpointType.CAMEL, GOOGLE_CHAT_ENDPOINT_SUBTYPE),
            Arguments.of(EndpointType.CAMEL, SLACK_ENDPOINT_SUBTYPE),
            Arguments.of(EndpointType.CAMEL, TEAMS_ENDPOINT_SUBTYPE)
        );
    }
}
