package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.connectors.InMemorySink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob testee;

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    @TestTransaction
    void shouldSentTwoAggregationsToKafkaTopic() {
        System.setProperty("notifications.aggregator.email.subscription.periodic.cron.enabled", "true");

        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "rhel", "unknown-application", "somePolicyId", "someHostId");

        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink("aggregation");

        assertEquals(2, results.received().size());

        final String firstAggregation = results.received().get(0).getPayload();
        assertTrue(firstAggregation.contains("accountId\":\"tenant"));
        assertTrue(firstAggregation.contains("bundle\":\"rhel"));
        assertTrue(firstAggregation.contains("application\":\"policies"));
        assertTrue(firstAggregation.contains("subscriptionType\":\"DAILY"));

        final String secondAggregation = results.received().get(1).getPayload();
        assertTrue(secondAggregation.contains("accountId\":\"tenant"));
        assertTrue(secondAggregation.contains("bundle\":\"rhel"));
        assertTrue(secondAggregation.contains("application\":\"unknown-application"));
        assertTrue(secondAggregation.contains("subscriptionType\":\"DAILY"));
    }

    @Test
    @TestTransaction
    void shouldNotChangeSomethingWhenCreatingSubscription() {
        helpers.subscribe("tenant", "admin", "rhel", "policies");
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = emailAggregations.get(0);
        assertEquals("tenant", aggregationCommand.getAggregationKey().getAccountId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    @TestTransaction
    void shouldNotChangeSomethingWhenRemovingSubscription() {
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.unsubscribe("tenant", "admin", "rhel", "policies");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = emailAggregations.get(0);
        assertEquals("tenant", aggregationCommand.getAggregationKey().getAccountId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    @TestTransaction
    void shouldProcessFourSubscriptions() {
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessOneSubscriptionOnly() {
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = emailAggregations.get(0);
        assertEquals("tenant", aggregationCommand.getAggregationKey().getAccountId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "policyId2", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "somePolicyId", "hostId1");
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "somePolicyId", "hostId2");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldNotProcessMailsWhenNewCronJobIsDisabledByDefault() {
        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        dailyEmailAggregationJob.processDailyEmail();
        verifyNoInteractions(emailAggregationResources);
    }

    @Test
    @TestTransaction
    void shouldNotProcessMailsWhenNewCronJobIsDisabledByEnvironmentVariable() {
        System.setProperty("notifications.aggregator.email.subscription.periodic.cron.enabled", "false");

        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        dailyEmailAggregationJob.processDailyEmail();
        verifyNoInteractions(emailAggregationResources);
    }
}
