package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CONSUMED_TIMER_NAME;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest {

    final String BUNDLE_NAME = "console";
    final String APP_NAME = "notifications";
    final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    EmailSubscriptionTypeProcessor testee;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @InjectMock
    EmailSender sender;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.removeDynamicTimer(AGGREGATION_CONSUMED_TIMER_NAME);
        featureFlipper.setUseDefaultTemplate(false);
    }

    @Test
    void shouldNotProcessWhenEndpointsAreNull() {
        testee.process(new Event(), null);
        verify(sender, never()).sendEmail(any(User.class), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        testee.process(new Event(), List.of());
        verify(sender, never()).sendEmail(any(User.class), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
    }

    @Test
    void shouldSuccessfullySendTwoAggregatedEmailToTwoRecipients() {
        when(sender.sendEmail(any(User.class), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class))).thenReturn(new NotificationHistory());
        shouldSuccessfullySendTwoAggregatedEmails(INGRESS_CHANNEL);
    }

    @Test
    void shouldSuccessfullySendOneAggregatedEmailWithTwoRecipients() {
        try {
            featureFlipper.setSendSingleEmailForMultipleRecipientsEnabled(true);
            NotificationHistory nh = new NotificationHistory();
            nh.setDetails(Map.of(EmailSubscriptionTypeProcessor.TOTAL_RECIPIENTS_KEY, 1));
            when(sender.sendEmail(any(Set.class), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class))).thenReturn(nh);
            shouldSuccessfullySendTwoAggregatedEmails(INGRESS_CHANNEL);
        } finally {
            featureFlipper.setSendSingleEmailForMultipleRecipientsEnabled(false);
        }
    }

    void shouldSuccessfullySendTwoAggregatedEmails(String channel) {

        micrometerAssertionHelper.saveCounterValuesBeforeTest(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, AGGREGATION_COMMAND_ERROR_COUNTER_NAME);

        // Because this test will use a real Payload Aggregator
        AggregationCommand aggregationCommand1 = new AggregationCommand(
            new EmailAggregationKey("org-1", "rhel", "policies"),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
            LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            DAILY
        );

        AggregationCommand aggregationCommand2 = new AggregationCommand(
            new EmailAggregationKey("org-2", "bundle-2", "app-2"),
            LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            LocalDateTime.now(ZoneOffset.UTC).plusDays(2),
            DAILY
        );

        createAggregatorEventTypeIfNeeded(channel);

        User user1 = new User();
        user1.setUsername("foo");
        User user2 = new User();
        user2.setUsername("bar");
        User user3 = new User();
        user3.setUsername("user3");

        when(recipientResolver.recipientUsers(any(), anySet(), any()))
            .then(invocation -> {
                    Set<RecipientSettings> list = invocation.getArgument(1);
                    if (list.isEmpty()) {
                        return Set.of(user1, user2);
                    }
                    return Set.of(user1, user2, user3);
                }
            );

        AggregationEmailTemplate blankAgg2 = resourceHelpers.createBlankAggregationEmailTemplate("bundle-2", "app-2");
        try {
            emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
            emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10), "user3"));

            inMemoryConnector.source(INGRESS_CHANNEL).send(buildAggregatorAction(aggregationCommand1));
            inMemoryConnector.source(INGRESS_CHANNEL).send(buildAggregatorAction(aggregationCommand2));

            micrometerAssertionHelper.awaitAndAssertTimerIncrement(AGGREGATION_CONSUMED_TIMER_NAME, 1);
            micrometerAssertionHelper.awaitAndAssertCounterIncrement(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, 2);
            micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, 0);
            micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_ERROR_COUNTER_NAME, 0);

            // Let's check that EndpointEmailSubscriptionResources#sendEmail was called for each aggregation.
            verify(emailAggregationRepository, timeout(5000L).times(1)).getEmailAggregation(
                eq(aggregationCommand1.getAggregationKey()),
                eq(aggregationCommand1.getStart()),
                eq(aggregationCommand1.getEnd()),
                eq(0),
                anyInt()
            );

            verify(emailAggregationRepository, timeout(5000L).times(1)).purgeOldAggregation(
                eq(aggregationCommand1.getAggregationKey()),
                eq(aggregationCommand1.getEnd())
            );
            verify(emailAggregationRepository, timeout(5000L).times(1)).getEmailAggregation(
                eq(aggregationCommand2.getAggregationKey()),
                eq(aggregationCommand2.getStart()),
                eq(aggregationCommand2.getEnd()),
                eq(0),
                anyInt()
            );
            verify(emailAggregationRepository, timeout(5000L).times(1)).purgeOldAggregation(
                eq(aggregationCommand2.getAggregationKey()),
                eq(aggregationCommand2.getEnd())
            );

            if (featureFlipper.isSendSingleEmailForMultipleRecipientsEnabled()) {
                verify(sender, timeout(5000L).times(1)).sendEmail(eq(Set.of(user1, user2)), any(), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
                verify(sender, timeout(5000L).times(1)).sendEmail(eq(Set.of(user3)), any(), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
            } else {
                verify(sender, timeout(5000L).times(1)).sendEmail(eq(user1), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
                verify(sender, timeout(5000L).times(1)).sendEmail(eq(user2), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
                verify(sender, timeout(5000L).times(1)).sendEmail(eq(user3), any(Event.class), any(TemplateInstance.class), any(TemplateInstance.class), anyBoolean(), any(Endpoint.class));
            }
            getEventHistory(channel);
        } finally {
            if (null != blankAgg2) {
                resourceHelpers.deleteEmailTemplatesById(blankAgg2.getId());
            }
        }
        micrometerAssertionHelper.clearSavedValues();
    }

    private void createAggregatorEventTypeIfNeeded(String channel) {
        if (INGRESS_CHANNEL.equals(channel)) {
            Application aggregatorApp = resourceHelpers.findOrCreateApplication(BUNDLE_NAME, APP_NAME);
            String eventTypeName = EVENT_TYPE_NAME;
            try {
                resourceHelpers.findEventType(aggregatorApp.getId(), eventTypeName);
            } catch (NoResultException nre) {
                resourceHelpers.createEventType(aggregatorApp.getId(), eventTypeName);
            }
        }
    }

    @Transactional
    public void getEventHistory(String channel) {
        if (INGRESS_CHANNEL.equals(channel)) {
            String query = "SELECT nh FROM NotificationHistory nh WHERE nh.event.eventType.name = 'aggregation'";
            List<NotificationHistory> histories = entityManager.createQuery(query, NotificationHistory.class).getResultList();
            assertFalse(histories.isEmpty());
            entityManager.createQuery("delete from NotificationHistory").executeUpdate();
            histories = entityManager.createQuery(query, NotificationHistory.class).getResultList();
            assertTrue(histories.isEmpty());
        }
    }

    private String buildAggregatorAction(AggregationCommand aggregationCommand) {

        Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
        Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
        payload.forEach(payloadBuilder::withAdditionalProperty);

        Action action = new Action.ActionBuilder()
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(EVENT_TYPE_NAME)
            .withOrgId(aggregationCommand.getAggregationKey().getOrgId())
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(payloadBuilder.build())
                    .build()))
            .build();

        return Parser.encode(action);
    }

    @Test
    void shouldSendDefaultEmailTemplatesFromDatabase() {
        shouldSendDefaultEmail();
    }

    @Test
    void shouldSendSingleEmailWithTwoRecieversUsingTemplatesFromDatabase() {
        try {
            featureFlipper.setSendSingleEmailForMultipleRecipientsEnabled(true);
            shouldSendDefaultEmail();
        } finally {
            featureFlipper.setSendSingleEmailForMultipleRecipientsEnabled(false);
        }
    }

    void shouldSendDefaultEmail() {
        try {
            featureFlipper.setUseDefaultTemplate(true);

            User user1 = new User();
            user1.setUsername("foo");
            User user2 = new User();
            user2.setUsername("bar");

            when(recipientResolver.recipientUsers(any(), any(), any(), eq(true)))
                .thenReturn(Set.of(user1, user2));

            Bundle bundle = new Bundle();
            bundle.setName("rhel");
            Application application = new Application();
            application.setId(UUID.randomUUID());
            application.setName("policies");
            application.setBundle(bundle);
            EventType eventType = new EventType();
            eventType.setId(UUID.randomUUID());
            eventType.setApplication(application);
            Event event = new Event();
            event.setOrgId("123456");
            event.setEventType(eventType);
            event.setId(UUID.randomUUID());
            event.setEventWrapper(new EventWrapperAction(
                    new Action.ActionBuilder()
                            .withOrgId("123456")
                            .withEventType("triggered")
                            .withApplication("policies")
                            .withBundle("rhel")
                            .withTimestamp(LocalDateTime.of(2022, 8, 24, 13, 30, 0, 0))
                            .withContext(
                                    new Context.ContextBuilder()
                                            .withAdditionalProperty("foo", "im foo")
                                            .withAdditionalProperty("bar", Map.of("baz", "im baz"))
                                            .build()
                            )
                            .withEvents(List.of(
                                    new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                            .withMetadata(new Metadata())
                                            .withPayload(new Payload())
                                            .build()
                            ))
                            .build()
            ));

            Endpoint endpoint = new Endpoint();
            endpoint.setProperties(new SystemSubscriptionProperties());
            endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

            testee.process(event, List.of(endpoint));
            if (featureFlipper.isSendSingleEmailForMultipleRecipientsEnabled()) {
                verify(sender, times(1)).sendEmail(eq(Set.of(user1, user2)), eq(event), any(TemplateInstance.class), any(TemplateInstance.class), eq(true), any(Endpoint.class));
            } else {
                verify(sender, times(1)).sendEmail(eq(user1), eq(event), any(TemplateInstance.class), any(TemplateInstance.class), eq(true), any(Endpoint.class));
                verify(sender, times(1)).sendEmail(eq(user2), eq(event), any(TemplateInstance.class), any(TemplateInstance.class), eq(true), any(Endpoint.class));
            }
        } finally {
            featureFlipper.setUseDefaultTemplate(false);
        }
    }

}
