package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationCronjobParameters;
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
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEmailAggregationJobAccordingOrgIdPrefTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob testee;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    FeatureFlipper featureFlipper;

    final AggregationCronjobParameters someOrgIdToProceed = new AggregationCronjobParameters("someOrgId", //
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), 0), //
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));

    final AggregationCronjobParameters anotherOrgIdToProceed = new AggregationCronjobParameters("anotherOrgId", //
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), 0), //
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));

    @BeforeEach
    void setUp() {
        featureFlipper.setAggregatorOrgPrefEnabled(true);
        helpers.purgeEmailAggregations();
        initAggregationParameters();
    }

    @AfterEach
    void tearDown() {
        featureFlipper.setAggregatorOrgPrefEnabled(false);
        helpers.purgeEmailAggregations();
        connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL).clear();
    }

    void initAggregationParameters() {
        helpers.purgeAggregationCronjobParameters();
        testee.defaultDailyDigestHour = LocalTime.now(ZoneOffset.UTC).getHour();
    }

    @Test
    @TestTransaction
    void shouldSentFourAggregationsToKafkaTopic() throws InterruptedException {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestHour(LocalTime.now(ZoneOffset.UTC).getHour());

        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(4, results.received().size());

        final String firstAggregation = results.received().get(2).getPayload();
        assertTrue(firstAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(firstAggregation.contains("bundle\":\"rhel"));
        assertTrue(firstAggregation.contains("application\":\"policies"));
        assertTrue(firstAggregation.contains("subscriptionType\":\"DAILY"));

        final String secondAggregation = results.received().get(0).getPayload();
        assertTrue(secondAggregation.contains("orgId\":\"anotherOrgId"));
        assertTrue(secondAggregation.contains("bundle\":\"rhel"));
        assertTrue(secondAggregation.contains("application\":\"policies"));
        assertTrue(secondAggregation.contains("subscriptionType\":\"DAILY"));

        final String thirdAggregation = results.received().get(3).getPayload();
        assertTrue(thirdAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(thirdAggregation.contains("bundle\":\"rhel"));
        assertTrue(thirdAggregation.contains("application\":\"unknown-application"));
        assertTrue(thirdAggregation.contains("subscriptionType\":\"DAILY"));

        final String fourthAggregation = results.received().get(1).getPayload();
        assertTrue(fourthAggregation.contains("orgId\":\"anotherOrgId"));
        assertTrue(fourthAggregation.contains("bundle\":\"rhel"));
        assertTrue(fourthAggregation.contains("application\":\"unknown-application"));
        assertTrue(fourthAggregation.contains("subscriptionType\":\"DAILY"));

    }

    @Test
    @TestTransaction
    void shouldSentTwoAggregationsToKafkaTopic() throws InterruptedException {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestHour(LocalTime.now(ZoneOffset.UTC).getHour());
        someOrgIdToProceed.setExpectedRunningTime(LocalTime.of(LocalTime.now(ZoneOffset.UTC).minusHours(2).getHour(), 0));
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        // Because we added time preferences for orgId someOrgId two hours in the past, those messages must me ignored
        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(2, results.received().size());

        final String secondAggregation = results.received().get(0).getPayload();
        assertTrue(secondAggregation.contains("orgId\":\"anotherOrgId"));
        assertTrue(secondAggregation.contains("bundle\":\"rhel"));
        assertTrue(secondAggregation.contains("application\":\"policies"));
        assertTrue(secondAggregation.contains("subscriptionType\":\"DAILY"));

        final String fourthAggregation = results.received().get(1).getPayload();
        assertTrue(fourthAggregation.contains("orgId\":\"anotherOrgId"));
        assertTrue(fourthAggregation.contains("bundle\":\"rhel"));
        assertTrue(fourthAggregation.contains("application\":\"unknown-application"));
        assertTrue(fourthAggregation.contains("subscriptionType\":\"DAILY"));

        // remove all preferences, and set default hour in the past, nothing should be processed
        helpers.purgeAggregationCronjobParameters();
        testee.setDefaultDailyDigestHour(LocalTime.now(ZoneOffset.UTC).getHour() - 2);
        connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL).clear();

        testee.processDailyEmail();

        results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(0, results.received().size());

        // Finally add preferences for org id someOrgId at the right Time
        helpers.purgeAggregationCronjobParameters();
        someOrgIdToProceed.setExpectedRunningTime(LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), 0));
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);
        LocalDateTime lastRun = someOrgIdToProceed.getLastRun();
        testee.processDailyEmail();
        AggregationCronjobParameters parameters = helpers.findAggregationCronjobParametersByOrgId(someOrgIdToProceed.getOrgId());
        assertNotNull(parameters);
        assertTrue(lastRun.isBefore(parameters.getLastRun()));

        results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(2, results.received().size());

        final String firstAggregation = results.received().get(0).getPayload();
        assertTrue(firstAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(firstAggregation.contains("bundle\":\"rhel"));
        assertTrue(firstAggregation.contains("application\":\"policies"));
        assertTrue(firstAggregation.contains("subscriptionType\":\"DAILY"));

        final String thirdAggregation = results.received().get(1).getPayload();
        assertTrue(thirdAggregation.contains("orgId\":\"someOrgId"));
        assertTrue(thirdAggregation.contains("bundle\":\"rhel"));
        assertTrue(thirdAggregation.contains("application\":\"unknown-application"));
        assertTrue(thirdAggregation.contains("subscriptionType\":\"DAILY"));
    }

    @Test
    @TestTransaction
    void shouldProcessFourPairs() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(4.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessFivePairs() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);
        helpers.addAggregationCronjobParameters(anotherOrgIdToProceed);

        testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(anotherOrgIdToProceed, someOrgIdToProceed), new CollectorRegistry());

        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(5.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessFourSubscriptions() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessOneSubscriptionOnly() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());

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
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId1");
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId2");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId2");
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);

        List<AggregationCommand> emailAggregations = null;
        emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());
        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessZeroAggregations() {
        helpers.addAggregationCronjobParameters(someOrgIdToProceed);
        final List<AggregationCommand> emailAggregations = testee.processAggregateEmails(LocalDateTime.now(UTC), Arrays.asList(someOrgIdToProceed), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }
}
