package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

    final AggregationOrgConfig someOrgIdToProceed = new AggregationOrgConfig("someOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));

    final AggregationOrgConfig anotherOrgIdToProceed = new AggregationOrgConfig("anotherOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
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
        helpers.purgeAggregationOrgConfig();
        testee.defaultDailyDigestHour = LocalTime.now(ZoneOffset.UTC).toString();
    }

    @Test
    @TestTransaction
    void shouldSentFourAggregationsToKafkaTopic() throws InterruptedException {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestHour(LocalTime.now(ZoneOffset.UTC).toString());

        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(4, results.received().size());

        final JsonObject firstAggregation = new JsonObject(results.received().get(2).getPayload());
        assertEquals("someOrgId", firstAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", firstAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("policies", firstAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", firstAggregation.getString("subscriptionType"));

        final JsonObject secondAggregation = new JsonObject(results.received().get(0).getPayload());
        assertEquals("anotherOrgId", secondAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", secondAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("policies", secondAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", secondAggregation.getString("subscriptionType"));

        final JsonObject thirdAggregation = new JsonObject(results.received().get(3).getPayload());
        assertEquals("someOrgId", thirdAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", thirdAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("unknown-application", thirdAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", thirdAggregation.getString("subscriptionType"));

        final JsonObject fourthAggregation = new JsonObject(results.received().get(1).getPayload());
        assertEquals("anotherOrgId", fourthAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", fourthAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("unknown-application", fourthAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", fourthAggregation.getString("subscriptionType"));
    }

    @Test
    @TestTransaction
    void shouldSentTwoAggregationsToKafkaTopic() throws InterruptedException {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestHour(now.toString());
        someOrgIdToProceed.setScheduledExecutionTime(LocalTime.of(LocalTime.now(ZoneOffset.UTC).minusHours(2).getHour(), 0));
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        // Because we added time preferences for orgId someOrgId two hours in the past, those messages must me ignored
        testee.processDailyEmail();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(2, results.received().size());

        final JsonObject secondAggregation = new JsonObject(results.received().get(0).getPayload());
        assertEquals("anotherOrgId", secondAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", secondAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("policies", secondAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", secondAggregation.getString("subscriptionType"));

        final JsonObject fourthAggregation = new JsonObject(results.received().get(1).getPayload());
        assertEquals("anotherOrgId", fourthAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", fourthAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("unknown-application", fourthAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", fourthAggregation.getString("subscriptionType"));

        // remove all preferences, and set default hour in the past, nothing should be processed
        helpers.purgeAggregationOrgConfig();
        testee.setDefaultDailyDigestHour(LocalTime.of(now.getHour() - 2, now.getMinute()).toString());
        connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL).clear();

        testee.processDailyEmail();

        assertEquals(0, results.received().size());

        // Finally add preferences for org id someOrgId at the right Time
        helpers.purgeAggregationOrgConfig();
        someOrgIdToProceed.setScheduledExecutionTime(LocalTime.now(ZoneOffset.UTC));
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        LocalDateTime lastRun = someOrgIdToProceed.getLastRun();
        testee.processDailyEmail();
        AggregationOrgConfig parameters = helpers.findAggregationOrgConfigByOrgId(someOrgIdToProceed.getOrgId());
        assertNotNull(parameters);
        assertTrue(lastRun.isBefore(parameters.getLastRun()));

        results = connector.sink(DailyEmailAggregationJob.AGGREGATION_CHANNEL);
        assertEquals(2, results.received().size());

        final JsonObject firstAggregation = new JsonObject(results.received().get(0).getPayload());
        assertEquals("someOrgId", firstAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", firstAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("policies", firstAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", firstAggregation.getString("subscriptionType"));

        final JsonObject thirdAggregation = new JsonObject(results.received().get(1).getPayload());
        assertEquals("someOrgId", thirdAggregation.getJsonObject("aggregationKey").getString("orgId"));
        assertEquals("rhel", thirdAggregation.getJsonObject("aggregationKey").getString("bundle"));
        assertEquals("unknown-application", thirdAggregation.getJsonObject("aggregationKey").getString("application"));
        assertEquals("DAILY", thirdAggregation.getString("subscriptionType"));
    }

    @Test
    @TestTransaction
    void shouldProcessOnePairRegardingExecutionTime() {
        helpers.addEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withMinute(32);

        AggregationOrgConfig tooLateOrgIdToProceed = new AggregationOrgConfig("tooLateOrgId",
            LocalTime.of(now.getHour(), 15),
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));
        AggregationOrgConfig onTimeOrgIdToProceed = new AggregationOrgConfig("onTimeOrgId",
            LocalTime.of(now.getHour(), 30),
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));
        AggregationOrgConfig toSoonOrgIdToProceed = new AggregationOrgConfig("tooSoonOrgId",
            LocalTime.of(now.getHour(), 45),
            LocalDateTime.now(ZoneOffset.UTC).minus(1, ChronoUnit.DAYS));

        helpers.addAggregationOrgConfig(tooLateOrgIdToProceed);
        helpers.addAggregationOrgConfig(onTimeOrgIdToProceed);
        helpers.addAggregationOrgConfig(toSoonOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessOnePairAtMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0);

        helpers.addEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId", now);
        helpers.addEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId", now);
        helpers.addEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId", now);

        AggregationOrgConfig tooLateOrgIdToProceed = new AggregationOrgConfig("tooLateOrgId",
            LocalTime.of(23, 45),
            now.minus(1, ChronoUnit.DAYS));
        AggregationOrgConfig onTimeOrgIdToProceed = new AggregationOrgConfig("onTimeOrgId",
            LocalTime.of(0, 0),
            now.minus(1, ChronoUnit.DAYS));
        AggregationOrgConfig toSoonOrgIdToProceed = new AggregationOrgConfig("tooSoonOrgId",
            LocalTime.of(0, 15),
            now.minus(1, ChronoUnit.DAYS));

        helpers.addAggregationOrgConfig(tooLateOrgIdToProceed);
        helpers.addAggregationOrgConfig(onTimeOrgIdToProceed);
        helpers.addAggregationOrgConfig(toSoonOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessFourPairs() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
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
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        helpers.addAggregationOrgConfig(anotherOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(5.0, pairsProcessed.get());
    }

    @Test
    @TestTransaction
    void shouldProcessFourAggregations() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessOneAggregationOnly() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

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
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId1");
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId2");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "someRhel", "somePolicies", "somePolicyId", "hostId2");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        List<AggregationCommand> emailAggregations = null;
        emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
        assertEquals(1, emailAggregations.size());
    }

    @Test
    @TestTransaction
    void shouldProcessZeroAggregations() {
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }
}
