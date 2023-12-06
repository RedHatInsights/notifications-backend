package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CONSUMED_TIMER_NAME;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest {

    final String BUNDLE_NAME = "console";
    final String APP_NAME = "notifications";
    final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @InjectMock
    ConnectorSender connectorSender;

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
    void shouldSuccessfullySendOneAggregatedEmailWithTwoRecipients() {
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

        createAggregatorEventTypeIfNeeded();

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

            ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(connectorSender, timeout(5000L).times(2)).send(any(Event.class), any(Endpoint.class), argumentCaptor.capture());
            List<JsonObject> capturedPayloads = argumentCaptor.getAllValues();
            assertTrue(
                    capturedPayloads.stream().anyMatch(capturedPayload -> {
                        JsonArray subscribers = capturedPayload.getJsonArray("subscribers");
                        return subscribers.size() == 2 && subscribers.contains(user1.getUsername()) && subscribers.contains(user2.getUsername());
                    })
            );
            assertTrue(
                    capturedPayloads.stream().anyMatch(capturedPayload -> {
                        JsonArray subscribers = capturedPayload.getJsonArray("subscribers");
                        return subscribers.size() == 1 && subscribers.contains(user3.getUsername());
                    })
            );
        } finally {
            if (null != blankAgg2) {
                resourceHelpers.deleteEmailTemplatesById(blankAgg2.getId());
            }
        }
        micrometerAssertionHelper.clearSavedValues();
    }

    private void createAggregatorEventTypeIfNeeded() {
        Application aggregatorApp = resourceHelpers.findOrCreateApplication(BUNDLE_NAME, APP_NAME);
        String eventTypeName = EVENT_TYPE_NAME;
        try {
            resourceHelpers.findEventType(aggregatorApp.getId(), eventTypeName);
        } catch (NoResultException nre) {
            resourceHelpers.createEventType(aggregatorApp.getId(), eventTypeName);
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
}
