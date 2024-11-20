package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Inject
    ObjectMapper objectMapper;

    final AggregationOrgConfig someOrgIdToProceed = new AggregationOrgConfig("someOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

    final AggregationOrgConfig anotherOrgIdToProceed = new AggregationOrgConfig("anotherOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

    @BeforeEach
    void setUp() {
        helpers.purgeEmailAggregations();
        initAggregationParameters();
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEmailAggregations();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    void initAggregationParameters() {
        helpers.purgeAggregationOrgConfig();
        testee.defaultDailyDigestTime = LocalTime.now(ZoneOffset.UTC);
    }

    List<AggregationCommand> getRecordsFromKafka() {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL);
        for (Message message : results.received()) {
            Action action = Parser.decode(String.valueOf(message.getPayload()));
            for (Event event : action.getEvents()) {
                aggregationCommands.add(objectMapper.convertValue(event.getPayload().getAdditionalProperties(), AggregationCommand.class));
            }
        }

        return aggregationCommands;
    }

    @Test
    void shouldSentFourAggregationsToKafkaTopic() {

        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestTime(testee.computeScheduleExecutionTime().toLocalTime());

        testee.processDailyEmail();

        List<AggregationCommand> listCommand = getRecordsFromKafka();
        assertEquals(4, listCommand.size());
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "unknown-application");
        checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application");
    }

    @Test
    void shouldSentTwoAggregationsToKafkaTopic() {
        LocalTime now = testee.computeScheduleExecutionTime().toLocalTime();
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        testee.setDefaultDailyDigestTime(now);
        someOrgIdToProceed.setScheduledExecutionTime(LocalTime.of(LocalTime.now(ZoneOffset.UTC).minusHours(2).getHour(), 0));
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        // Because we added time preferences for orgId someOrgId two hours in the past, those messages must me ignored
        testee.processDailyEmail();

        List<AggregationCommand> listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        checkAggCommand(listCommand, "anotherOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "unknown-application");

        // remove all preferences, and set default hour in the past, nothing should be processed
        helpers.purgeAggregationOrgConfig();
        testee.setDefaultDailyDigestTime(now.minusHours(2));
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();

        testee.processDailyEmail();

        assertEquals(0, getRecordsFromKafka().size());

        // Finally add preferences for org id someOrgId at the right Time
        helpers.purgeAggregationOrgConfig();
        someOrgIdToProceed.setScheduledExecutionTime(testee.computeScheduleExecutionTime().toLocalTime());
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        LocalDateTime lastRun = someOrgIdToProceed.getLastRun();
        testee.processDailyEmail();
        AggregationOrgConfig parameters = helpers.findAggregationOrgConfigByOrgId(someOrgIdToProceed.getOrgId());
        assertNotNull(parameters);
        assertTrue(lastRun.isBefore(parameters.getLastRun()));

        listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application");
    }

    private void checkAggCommand(List<AggregationCommand> commands, String orgId, String bundle, String application) {
        assertTrue(commands.stream().anyMatch(
            com -> orgId.equals(com.getAggregationKey().getOrgId()) &&
                bundle.equals(com.getAggregationKey().getBundle()) &&
                application.equals(com.getAggregationKey().getApplication()) &&
                DAILY.equals(com.getSubscriptionType())
            ));
    }

    @Test
    void shouldProcessOnePairRegardingExecutionTime() {
        helpers.addEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withMinute(32);

        AggregationOrgConfig tooLateOrgIdToProceed = new AggregationOrgConfig("tooLateOrgId",
            LocalTime.of(now.getHour(), 15),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        AggregationOrgConfig onTimeOrgIdToProceed = new AggregationOrgConfig("onTimeOrgId",
            LocalTime.of(now.getHour(), 30),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        AggregationOrgConfig toSoonOrgIdToProceed = new AggregationOrgConfig("tooSoonOrgId",
            LocalTime.of(now.getHour(), 45),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

        helpers.addAggregationOrgConfig(tooLateOrgIdToProceed);
        helpers.addAggregationOrgConfig(onTimeOrgIdToProceed);
        helpers.addAggregationOrgConfig(toSoonOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
    void shouldProcessOnePairAtMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0);

        helpers.addEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId", now);
        helpers.addEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId", now);
        helpers.addEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId", now);

        AggregationOrgConfig tooLateOrgIdToProceed = new AggregationOrgConfig("tooLateOrgId",
            LocalTime.of(23, 45),
            now.minusDays(1));
        AggregationOrgConfig onTimeOrgIdToProceed = new AggregationOrgConfig("onTimeOrgId",
            LocalTime.of(0, 0),
            now.minusDays(1));
        AggregationOrgConfig toSoonOrgIdToProceed = new AggregationOrgConfig("tooSoonOrgId",
            LocalTime.of(0, 15),
            now.minusDays(1));

        helpers.addAggregationOrgConfig(tooLateOrgIdToProceed);
        helpers.addAggregationOrgConfig(onTimeOrgIdToProceed);
        helpers.addAggregationOrgConfig(toSoonOrgIdToProceed);

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
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
    void shouldProcessOneAggregationOnly() {
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = (AggregationCommand) emailAggregations.get(0);
        assertEquals("someOrgId", aggregationCommand.getAggregationKey().getOrgId());
        assertEquals("rhel", aggregationCommand.getAggregationKey().getBundle());
        assertEquals("policies", aggregationCommand.getAggregationKey().getApplication());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addEmailAggregation("someOrgId", "someRhel", "somePolicies", "policyId2", "someHostId");
        helpers.addEmailAggregation("shouldBeIgnoredOrgId", "someRhel", "somePolicies", "policyId1", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
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
    void shouldProcessZeroAggregations() {
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        final List<AggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }
}
