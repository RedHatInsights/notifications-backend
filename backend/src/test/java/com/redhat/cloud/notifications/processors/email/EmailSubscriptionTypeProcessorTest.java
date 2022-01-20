package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.templates.Blank;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CHANNEL;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_COMMAND_REJECTED_COUNTER_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailSubscriptionTypeProcessorTest extends DbIsolatedTest {

    @Inject
    EmailSubscriptionTypeProcessor testee;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

    @InjectMock
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Test
    void shouldNotProcessWhenEndpointsAreNull() {
        final Multi<NotificationHistory> process = testee.process(new Event(), null);

        process.subscribe()
                .withSubscriber(AssertSubscriber.create())
                .assertCompleted()
                .assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldNotProcessWhenEndpointsAreEmpty() {
        final Multi<NotificationHistory> process = testee.process(new Event(), List.of());

        process.subscribe()
                .withSubscriber(AssertSubscriber.create())
                .assertCompleted()
                .assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldSuccessfullySendEmail() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, AGGREGATION_COMMAND_ERROR_COUNTER_NAME);

        AggregationCommand aggregationCommand1 = new AggregationCommand(
                new EmailAggregationKey("account-1", "bundle-1", "app-1"),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                DAILY
        );
        AggregationCommand aggregationCommand2 = new AggregationCommand(
                new EmailAggregationKey("account-2", "bundle-2", "app-2"),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(2),
                DAILY
        );

        when(emailTemplateFactory.get(anyString(), anyString())).thenReturn(new Blank());

        inMemoryConnector.source(AGGREGATION_CHANNEL).send(Json.encode(aggregationCommand1));
        inMemoryConnector.source(AGGREGATION_CHANNEL).send(Json.encode(aggregationCommand2));

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, 2);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_ERROR_COUNTER_NAME, 0);

        // Let's check that EndpointEmailSubscriptionResources#sendEmail was called for each aggregation.
        verify(emailAggregationRepository, times(1)).getEmailAggregation(
                eq(aggregationCommand1.getAggregationKey()),
                eq(aggregationCommand1.getStart()),
                eq(aggregationCommand1.getEnd())
        );

        verify(emailAggregationRepository, times(1)).purgeOldAggregation(
                eq(aggregationCommand1.getAggregationKey()),
                eq(aggregationCommand1.getEnd())
        );
        verify(emailAggregationRepository, times(1)).getEmailAggregation(
                eq(aggregationCommand2.getAggregationKey()),
                eq(aggregationCommand2.getStart()),
                eq(aggregationCommand2.getEnd())
        );
        verify(emailAggregationRepository, times(1)).purgeOldAggregation(
                eq(aggregationCommand2.getAggregationKey()),
                eq(aggregationCommand2.getEnd())
        );
        verifyNoMoreInteractions(emailAggregationRepository);

        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void consumeEmailAggregationsShouldNotThrowInCaseOfInvalidPayload() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, AGGREGATION_COMMAND_ERROR_COUNTER_NAME);

        inMemoryConnector.source(AGGREGATION_CHANNEL).send("I am not valid!");
        micrometerAssertionHelper.awaitAndAssertCounterIncrement(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_ERROR_COUNTER_NAME, 0);

        micrometerAssertionHelper.clearSavedValues();
    }
}
