package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javax.inject.Inject;
import java.time.LocalDateTime;
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
    ResourceHelpers helpers;

    @BeforeAll
    void init() {
        testee = new DailyEmailAggregationJob(emailAggregationResources);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeAggregations();
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
        testee.processDailyEmail();
        verifyNoInteractions(mock(EmailAggregationResources.class));
    }
}
