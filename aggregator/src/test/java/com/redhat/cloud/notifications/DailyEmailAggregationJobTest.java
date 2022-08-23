package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.EmailSubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob testee;

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void setUp() {
        helpers.purgeEmailAggregations();
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEmailAggregations();
    }

    @Test
    @TestTransaction
    void shouldSentTwoAggregationsToKafkaTopic() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");

        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);

        assertEquals(2, results.received().size());

        final String firstAggregation = results.received().get(0).getPayload();
        assertTrue(firstAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(firstAggregation.contains("bundle\":\"rhel"));
        assertTrue(firstAggregation.contains("application\":\"policies"));
        assertTrue(firstAggregation.contains("subscriptionType\":\"DAILY"));

        final String secondAggregation = results.received().get(1).getPayload();
        assertTrue(firstAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(secondAggregation.contains("bundle\":\"rhel"));
        assertTrue(secondAggregation.contains("application\":\"unknown-application"));
        assertTrue(secondAggregation.contains("subscriptionType\":\"DAILY"));
    }

    @Test
    @TestTransaction
    void shouldProcessFourPairs() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");

        testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(4.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessFourSubscriptions() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessOneSubscriptionOnly() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = emailAggregations.get(0);
        assertEquals("someOrgId", aggregationCommand.getAggregationKey().getOrgId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "policyId2", "someHostId");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId1");
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId2");

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessZeroAggregations() {
        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }
}
