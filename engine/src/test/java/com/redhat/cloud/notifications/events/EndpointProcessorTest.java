package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatProcessor;
import com.redhat.cloud.notifications.processors.camel.slack.SlackProcessor;
import com.redhat.cloud.notifications.processors.camel.teams.TeamsProcessor;
import com.redhat.cloud.notifications.processors.drawer.DrawerProcessor;
import com.redhat.cloud.notifications.processors.email.EmailProcessor;
import com.redhat.cloud.notifications.processors.eventing.EventingProcessor;
import com.redhat.cloud.notifications.processors.pagerduty.PagerDutyProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.events.EndpointProcessor.SLACK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;
import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
public class EndpointProcessorTest {

    @Inject
    EndpointProcessor endpointProcessor;

    /**
     * The processor is mocked to avoid {@link #testTestEndpointFetchedById()}
     * throwing exceptions due to passing fixture data to the Slack processor.
     */
    @InjectMock
    SlackProcessor slackProcessor;

    @InjectMock
    WebhookTypeProcessor webhookProcessor;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectSpy
    EngineConfig engineConfig;

    @InjectMock
    EventingProcessor camelProcessor;

    @InjectMock
    EmailProcessor emailConnectorProcessor;

    @InjectMock
    TeamsProcessor teamsProcessor;

    @InjectMock
    GoogleChatProcessor googleChatProcessor;

    @InjectMock
    DrawerProcessor drawerProcessor;

    @InjectMock
    PagerDutyProcessor pagerDutyProcessor;

    /**
     * Tests that when an "integration customer test" event is processed, the
     * corresponding endpoint is fetched by the UUID that gets set in the
     * action's context.
     */
    @Test
    void testTestEndpointFetchedById() {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";
        final UUID endpointUuid = UUID.randomUUID();

        final Endpoint endpointFixture = new Endpoint();
        endpointFixture.setId(endpointUuid);
        endpointFixture.setOrgId(orgId);
        endpointFixture.setSubType("slack");
        endpointFixture.setType(EndpointType.CAMEL);

        // Avoids the "NulLPointerException" in the "List.of" statement.
        Mockito.when(this.endpointRepository.findByUuidAndOrgId(endpointUuid, orgId)).thenReturn(endpointFixture);

        // Create the action with the endpoint reference and then a corresponding event for the processor.
        final Action testAction = TestEventHelper.createTestAction(endpointUuid, orgId);

        // Convert the action to JSON and back to simulate the event going
        // through Kafka. If not, some additional properties of the context are
        // not serialized as String, and won't match all the types and the way
        // they get serialized when sent via Kafka and received via Kafka as
        // well.
        final String jsonAction = Parser.encode(testAction);
        final Action rawAction = Parser.decode(jsonAction);

        final Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(rawAction));
        event.setId(rawAction.getId());
        event.setOrgId(orgId);
        event.setEventType(new EventType());

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(1)).findByUuidAndOrgId(endpointUuid, orgId);
        Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
    }

    /**
     * Tests that when a regular, non-test event is received, the corresponding
     * target endpoints get fetched by the event's org id and event type.
     */
    @Test
    void testEndpointFetchedAsRegular() {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";

        // Create an action.
        final Action testAction = TestEventHelper.createTestAction(UUID.randomUUID(), orgId);
        testAction.setApplication("non test application");
        testAction.setBundle("non test bundle");
        testAction.setEventType("non test event type");

        final UUID eventTypeId = UUID.randomUUID();
        final EventType eventType = new EventType();
        eventType.setId(eventTypeId);

        final Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(testAction));
        event.setEventType(eventType);
        event.setId(testAction.getId());
        event.setOrgId(orgId);

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(0)).findByUuidAndOrgId(Mockito.any(), Mockito.anyString());
        Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpoints(orgId, eventType);
    }

    /**
     * Tests Event-Driven Ansible endpoint type being aliased to a WebHook processor.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTestEndpointAnsibleAliasToWebhook(final boolean useEndpointToEventTypeDirectLink) {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";
        final UUID endpointUuid = UUID.randomUUID();
        Mockito.when(engineConfig.isUseDirectEndpointToEventTypeEnabled()).thenReturn(useEndpointToEventTypeDirectLink);

        final Endpoint endpointFixture = new Endpoint();
        endpointFixture.setId(endpointUuid);
        endpointFixture.setOrgId(orgId);
        endpointFixture.setType(EndpointType.ANSIBLE);

        Action action = buildAction(orgId);

        final EventType eventType = new EventType();
        eventType.setId(UUID.randomUUID());

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(eventType);
        event.setOrgId(orgId);

        Mockito.when(this.endpointRepository.getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(endpointFixture));
        Mockito.when(this.endpointRepository.getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(endpointFixture));

        Mockito.doNothing().when(this.webhookProcessor).process(Mockito.any(Event.class), Mockito.anyList());

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(0)).findByUuidAndOrgId(endpointUuid, orgId);
        Mockito.verify(this.webhookProcessor, Mockito.times(1)).process(eq(event), Mockito.anyList());

        if (useEndpointToEventTypeDirectLink) {
            Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class));
            Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
        } else {
            Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
            Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointRestrictionAccordingEventType(final boolean isEventTypeRestrictedToRecipientsIntegrations) {
        Mockito.when(engineConfig.isEmailsOnlyModeEnabled()).thenReturn(Boolean.FALSE);
        Mockito.when(engineConfig.isUseDirectEndpointToEventTypeEnabled()).thenReturn(Boolean.TRUE);

        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";

        final Endpoint webhookEndpoint = new Endpoint();
        webhookEndpoint.setType(EndpointType.WEBHOOK);

        final Endpoint slackEndpoint = new Endpoint();
        slackEndpoint.setSubType(SLACK_ENDPOINT_SUBTYPE);
        slackEndpoint.setType(EndpointType.CAMEL);

        final Endpoint teamsEndpoint = new Endpoint();
        teamsEndpoint.setSubType(TEAMS_ENDPOINT_SUBTYPE);
        teamsEndpoint.setType(EndpointType.CAMEL);

        final Endpoint googleEndpoint = new Endpoint();
        googleEndpoint.setSubType(GOOGLE_CHAT_ENDPOINT_SUBTYPE);
        googleEndpoint.setType(EndpointType.CAMEL);

        final Endpoint otherEndpoint = new Endpoint();
        otherEndpoint.setSubType("other");
        otherEndpoint.setType(EndpointType.CAMEL);

        final Endpoint ansibleEndpoint = new Endpoint();
        ansibleEndpoint.setType(EndpointType.ANSIBLE);

        final Endpoint drawerEndpoint = new Endpoint();
        drawerEndpoint.setType(EndpointType.DRAWER);

        final Endpoint pagerDutyEndpoint = new Endpoint();
        pagerDutyEndpoint.setType(EndpointType.PAGERDUTY);

        final Endpoint emailEndpoint = new Endpoint();
        emailEndpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        Mockito.when(endpointRepository.getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class)))
            .thenReturn(List.of(webhookEndpoint, slackEndpoint, ansibleEndpoint, drawerEndpoint, pagerDutyEndpoint, emailEndpoint, teamsEndpoint, googleEndpoint, otherEndpoint));

        Action action = buildAction(orgId);

        // Convert the action to JSON and back to simulate the event going
        // through Kafka. If not, some additional properties of the context are
        // not serialized as String, and won't match all the types and the way
        // they get serialized when sent via Kafka and received via Kafka as
        // well.
        final String jsonAction = Parser.encode(action);
        final Action rawAction = Parser.decode(jsonAction);

        final Event event = new Event();
        event.setEventWrapper(new EventWrapperAction(rawAction));
        event.setId(rawAction.getId());
        event.setOrgId(orgId);
        event.setEventType(new EventType());
        event.getEventType().setRestrictToRecipientsIntegrations(isEventTypeRestrictedToRecipientsIntegrations);
        endpointProcessor.process(event);

        Mockito.verify(drawerProcessor, Mockito.times(1)).process(any(), any());
        Mockito.verify(emailConnectorProcessor, Mockito.times(1)).process(any(), any());

        if (isEventTypeRestrictedToRecipientsIntegrations) {
            Mockito.verifyNoInteractions(webhookProcessor);
            Mockito.verifyNoInteractions(slackProcessor);
            Mockito.verifyNoInteractions(pagerDutyProcessor);
            Mockito.verifyNoInteractions(teamsProcessor);
            Mockito.verifyNoInteractions(googleChatProcessor);
            Mockito.verifyNoInteractions(camelProcessor);
        } else {
            Mockito.verify(webhookProcessor, Mockito.times(2)).process(any(), any());
            Mockito.verify(slackProcessor, Mockito.times(1)).process(any(), any());
            Mockito.verify(pagerDutyProcessor, Mockito.times(1)).process(any(), any());
            Mockito.verify(teamsProcessor, Mockito.times(1)).process(any(), any());
            Mockito.verify(googleChatProcessor, Mockito.times(1)).process(any(), any());
            Mockito.verify(camelProcessor, Mockito.times(1)).process(any(), any());
        }
    }

    /**
     * Tests Event-Driven Ansible endpoint type being aliased to a WebHook processor.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBlacklistedEndpoints(final boolean useEndpointToEventTypeDirectLink) {
        // Create an Endpoint which will be simulated to be fetched from the database.
        final String orgId = "test-org-id";
        final UUID endpointUuid = UUID.randomUUID();
        final UUID endpointUuid2 = UUID.randomUUID();
        final UUID endpointUuid3 = UUID.randomUUID();
        Mockito.when(engineConfig.isUseDirectEndpointToEventTypeEnabled()).thenReturn(useEndpointToEventTypeDirectLink);

        final Endpoint blacklistedEndpoint = new Endpoint();
        blacklistedEndpoint.setId(endpointUuid);
        blacklistedEndpoint.setOrgId(orgId);
        blacklistedEndpoint.setType(EndpointType.ANSIBLE);

        final Endpoint regularEndpoint = new Endpoint();
        regularEndpoint.setId(endpointUuid2);
        regularEndpoint.setOrgId(orgId);
        regularEndpoint.setType(EndpointType.WEBHOOK);

        final Endpoint systemEndpoint = new Endpoint();
        systemEndpoint.setId(endpointUuid3);
        systemEndpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        Action action = buildAction(orgId);

        final EventType eventType = new EventType();
        eventType.setId(UUID.randomUUID());

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventWrapper(new EventWrapperAction(action));
        event.setEventType(eventType);
        event.setOrgId(orgId);

        Mockito.when(this.endpointRepository.getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(regularEndpoint, blacklistedEndpoint, systemEndpoint));
        Mockito.when(this.endpointRepository.getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(regularEndpoint, blacklistedEndpoint, systemEndpoint));

        Mockito.when(engineConfig.isBlacklistedEndpoint(eq(endpointUuid))).thenReturn(true);
        // endpointUuid3 blacklisting should fe ignored because it's a system endpoint (org_id is null)
        Mockito.when(engineConfig.isBlacklistedEndpoint(eq(endpointUuid3))).thenReturn(true);

        this.endpointProcessor.process(event);

        Mockito.verify(this.endpointRepository, Mockito.times(0)).findByUuidAndOrgId(endpointUuid, orgId);
        Mockito.verify(this.webhookProcessor, Mockito.times(1)).process(eq(event), Mockito.anyList());
        Mockito.verify(this.emailConnectorProcessor, Mockito.times(1)).process(eq(event), Mockito.anyList());
        Mockito.verify(this.engineConfig, Mockito.times(2)).isBlacklistedEndpoint(any(UUID.class));

        if (useEndpointToEventTypeDirectLink) {
            Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class));
            Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
        } else {
            Mockito.verify(this.endpointRepository, Mockito.times(1)).getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class));
            Mockito.verify(this.endpointRepository, Mockito.times(0)).getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class));
        }

        // all endpoints blacklisted
        Mockito.reset(this.webhookProcessor);
        Mockito.reset(this.emailConnectorProcessor);
        Mockito.reset(this.engineConfig);

        Mockito.when(this.endpointRepository.getTargetEndpoints(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(regularEndpoint, blacklistedEndpoint));
        Mockito.when(this.endpointRepository.getTargetEndpointsWithoutUsingBgs(Mockito.anyString(), Mockito.any(EventType.class))).thenReturn(List.of(regularEndpoint, blacklistedEndpoint));

        Mockito.when(engineConfig.isBlacklistedEndpoint(any(UUID.class))).thenReturn(true);

        this.endpointProcessor.process(event);

        Mockito.verify(this.webhookProcessor, Mockito.times(0)).process(eq(event), Mockito.anyList());
        Mockito.verify(this.emailConnectorProcessor, Mockito.times(0)).process(eq(event), Mockito.anyList());
        Mockito.verify(this.engineConfig, Mockito.times(2)).isBlacklistedEndpoint(any(UUID.class));
    }

    private static Action buildAction(String orgId) {
        Action action = new Action.ActionBuilder()
            .withBundle("rhel")
            .withApplication("policies")
            .withEventType("policy-triggered")
            .withOrgId(orgId)
            .withTimestamp(LocalDateTime.now(UTC))
            .withContext(new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", "6ad30f3e-0497-4e74-99f1-b3f9a6120a6f")
                .withAdditionalProperty("display_name", "my-computer")
                .build()
            )
            .withEvents(List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(new Payload.PayloadBuilder()
                        .withAdditionalProperty("foo", "bar")
                        .build()
                    ).build()
            )).build();
        return action;
    }

}
