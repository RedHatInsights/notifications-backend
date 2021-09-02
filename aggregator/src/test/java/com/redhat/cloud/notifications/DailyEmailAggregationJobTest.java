package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.SetSystemProperty;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobTest {

    private DailyEmailAggregationJob testee;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ResourceHelpers helpers;

    @BeforeAll
    void init() {
        testee = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeAggregations();
    }

    @Test
    @SetSystemProperty(key = "notifications.aggregator.email.subscription.periodic.cron.enabled", value = "false")
    void shouldSendPayloadToKafkaTopic() throws JsonProcessingException {
        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        final CronJobRun cronJobRun = mock(CronJobRun.class);
        when(emailAggregationResources.getLastCronJobRun()).thenReturn(cronJobRun);
        when(cronJobRun.getLastRun()).thenReturn(LocalDateTime.now().minusYears(1337));

        final List<EmailAggregationKey> aggregationCommands = new LinkedList<>();
        aggregationCommands.add(new EmailAggregationKey("tenant", "rhel", "policies"));
        when(emailAggregationResources.getApplicationsWithPendingAggregation(any(), any())).thenReturn(aggregationCommands);

        final ObjectMapper objectMapper1 = mock(ObjectMapper.class);
        when(objectMapper1.writeValueAsString(anyString())).thenReturn("");

        // use InMemoryConnector to receive and test the payload

        dailyEmailAggregationJob.processDailyEmail();
    }

    @Test
    void shouldNotChangeSomethingWhenCreatingSubscription() {
        helpers.createDailySubscription("tenant", "admin", "rhel", "policies");
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
    void shouldNotChangeSomethingWhenRemovingSubscription() {
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.removeDailySubscription("tenant", "admin", "rhel", "policies");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = emailAggregations.get(0);
        assertEquals("tenant", aggregationCommand.getAggregationKey().getAccountId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldProcessFourSubscriptions() {
        helpers.addEmailAggregation("tenant", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tenant", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(4, emailAggregations.size());
    }

    @Test
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
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "policyId2", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "somePolicyId", "hostId1");
        helpers.addEmailAggregation("someTenant", "someRhel", "somePolicies", "somePolicyId", "hostId2");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldNotProcessMailsWhenNewCronJobIsDisabledByDefault() {
        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        dailyEmailAggregationJob.processDailyEmail();
        verifyNoInteractions(emailAggregationResources);
    }

    @Test
    @SetSystemProperty(key = "notifications.aggregator.email.subscription.periodic.cron.enabled", value = "false")
    void shouldNotProcessMailsWhenNewCronJobIsDisabledByEnvironmentVariable() {
        final EmailAggregationResources emailAggregationResources = mock(EmailAggregationResources.class);
        final ObjectMapper objectMapper = mock(ObjectMapper.class);
        DailyEmailAggregationJob dailyEmailAggregationJob = new DailyEmailAggregationJob(emailAggregationResources, objectMapper);

        dailyEmailAggregationJob.processDailyEmail();
        verifyNoInteractions(emailAggregationResources);
    }
}
