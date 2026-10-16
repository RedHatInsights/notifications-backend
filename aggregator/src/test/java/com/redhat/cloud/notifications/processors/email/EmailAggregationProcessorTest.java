package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.DailyEmailAggregationJob;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EventRepository;
import com.redhat.cloud.notifications.db.EventTypeRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of the direct-dispatch aggregation pipeline (pieces 2-5 of the
 * aggregator-hardening baseline port): data aggregation ({@link EmailAggregator}),
 * recipient resolution (mocked {@link ExternalRecipientsResolver}, same as engine's
 * equivalent test), and dispatch (mocked {@link ConnectorSender}), all driven through
 * {@link EmailAggregationProcessor#processBundleAggregation} directly -- the same entry
 * point {@link DailyEmailAggregationJob}'s direct-dispatch path calls.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailAggregationProcessorTest {

    @Inject
    EmailAggregationProcessor emailAggregationProcessor;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    EventRepository eventRepository;

    @InjectMock
    ExternalRecipientsResolver externalRecipientsResolver;

    @InjectMock
    ConnectorSender connectorSender;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void shouldSuccessfullySendAggregatedEmail() {
        double processedCountBefore = meterRegistry.counter(EmailAggregationProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME).count();

        String orgId = "org-" + new SecureRandom().nextInt();

        Action advisorAction = buildAdvisorAction(orgId);
        resourceHelpers.addEventEmailAggregation(orgId, "rhel", "advisor", LocalDateTime.now(UTC), Parser.encode(advisorAction), false);

        Application app = resourceHelpers.findOrCreateApplication("rhel", "advisor");
        EventAggregationCriterion aggregationKey = new EventAggregationCriterion(orgId, app.getBundleId(), app.getId(), "rhel", "advisor");
        AggregationCommand command = new AggregationCommand(
            aggregationKey, LocalDateTime.now(UTC).minusDays(1), LocalDateTime.now(UTC).plusDays(1), SubscriptionType.DAILY);

        User user = new User();
        user.setUsername("foo");
        user.setEmail("foo@redhat.com");
        when(externalRecipientsResolver.recipientUsers(anyString(), anySet(), anySet(), anySet(), anyBoolean(), any()))
            .thenReturn(Set.of(user));

        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setProperties(new SystemSubscriptionProperties());

        com.redhat.cloud.notifications.models.Event aggregatorEvent = buildAndPersistAggregatorEvent(orgId);

        emailAggregationProcessor.processBundleAggregation(List.of(command), aggregatorEvent, List.of(endpoint));

        ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(connectorSender, timeout(5000L)).send(eq(aggregatorEvent), eq(endpoint), argumentCaptor.capture());

        EmailNotification capturedEmailRequest = argumentCaptor.getValue().mapTo(EmailNotification.class);
        assertTrue(capturedEmailRequest.subscribers().contains("foo"), "the mocked recipient should be a subscriber on the outgoing notification");
        assertEquals("rhel", capturedEmailRequest.eventData().get("bundle_name"), "the outgoing notification should carry the aggregated bundle name");
        assertEquals(processedCountBefore + 1, meterRegistry.counter(EmailAggregationProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME).count(),
            "the processed-command counter should be incremented for the one successfully aggregated command");
    }

    @Test
    void shouldNotSendAggregatedEmailWhenNoEndpointsProvided() {
        String orgId = "org-" + new SecureRandom().nextInt();

        Application app = resourceHelpers.findOrCreateApplication("rhel", "advisor");
        EventAggregationCriterion aggregationKey = new EventAggregationCriterion(orgId, app.getBundleId(), app.getId(), "rhel", "advisor");
        AggregationCommand command = new AggregationCommand(
            aggregationKey, LocalDateTime.now(UTC).minusDays(1), LocalDateTime.now(UTC).plusDays(1), SubscriptionType.DAILY);

        com.redhat.cloud.notifications.models.Event aggregatorEvent = buildAndPersistAggregatorEvent(orgId);

        // No underlying events were persisted for this org/app, so EmailAggregator.getAggregated()
        // returns no data for any user -- no email should be dispatched regardless of endpoints.
        emailAggregationProcessor.processBundleAggregation(List.of(command), aggregatorEvent, List.of());

        verifyNoInteractions(connectorSender);
    }

    private com.redhat.cloud.notifications.models.Event buildAndPersistAggregatorEvent(String orgId) {
        Action triggerAction = new Action.ActionBuilder()
            .withId(UUID.randomUUID())
            .withBundle(DailyEmailAggregationJob.BUNDLE_NAME)
            .withApplication(DailyEmailAggregationJob.APP_NAME)
            .withEventType(DailyEmailAggregationJob.EVENT_TYPE_NAME)
            .withOrgId(orgId)
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(List.of())
            .withContext(new Context.ContextBuilder()
                .withAdditionalProperty("single_daily_digest_enabled", true)
                .build())
            .build();

        EventType eventType = eventTypeRepository.getEventType(
            DailyEmailAggregationJob.BUNDLE_NAME, DailyEmailAggregationJob.APP_NAME, DailyEmailAggregationJob.EVENT_TYPE_NAME);
        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event(
            eventType, Parser.encode(triggerAction), new EventWrapperAction(triggerAction), Optional.empty(), null);
        return eventRepository.create(event);
    }

    private static Action buildAdvisorAction(String orgId) {
        return new Action.ActionBuilder()
            .withId(UUID.randomUUID())
            .withBundle("rhel")
            .withApplication("advisor")
            .withEventType("new-recommendation")
            .withOrgId(orgId)
            .withTimestamp(LocalDateTime.now())
            .withContext(new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", UUID.randomUUID().toString())
                .withAdditionalProperty("display_name", "my-computer")
                .withAdditionalProperty("tags", List.of())
                .build())
            .withEvents(List.of(new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(new Payload.PayloadBuilder()
                    .withAdditionalProperty(AdvisorEmailAggregator.RULE_ID, "test|Active_rule_1")
                    .withAdditionalProperty(AdvisorEmailAggregator.RULE_DESCRIPTION, "Active rule 1")
                    .withAdditionalProperty(AdvisorEmailAggregator.TOTAL_RISK, "3")
                    .withAdditionalProperty(AdvisorEmailAggregator.HAS_INCIDENT, "true")
                    .withAdditionalProperty(AdvisorEmailAggregator.RULE_URL, "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_1")
                    .build())
                .build()))
            .build();
    }
}
