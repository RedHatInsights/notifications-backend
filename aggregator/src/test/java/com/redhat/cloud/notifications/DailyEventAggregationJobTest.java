package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEventAggregationJobTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectSpy
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @InjectSpy
    AggregatorConfig aggregatorConfig;

    LocalDateTime baseReferenceTime;

    final AggregationOrgConfig someOrgIdToProceed = new AggregationOrgConfig("someOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

    final AggregationOrgConfig anotherOrgIdToProceed = new AggregationOrgConfig("anotherOrgId",
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

    @BeforeEach
    void setUp() {
        helpers.purgeEventAggregations();
        initAggregationParameters();
        when(aggregatorConfig.isAggregationBasedOnEventEnabled()).thenReturn(true);
        baseReferenceTime = dailyEmailAggregationJob.computeScheduleExecutionTime();

        when(dailyEmailAggregationJob.computeScheduleExecutionTime()).thenReturn(baseReferenceTime);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    void initAggregationParameters() {
        helpers.purgeAggregationOrgConfig();
        dailyEmailAggregationJob.defaultDailyDigestTime = LocalTime.now(ZoneOffset.UTC);
    }

    List<AggregationCommand> getRecordsFromKafka() {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL);
        for (Message message : results.received()) {
            Action action = Parser.decode(String.valueOf(message.getPayload()));
            for (Event event : action.getEvents()) {
                AggregationCommand aggCommand = objectMapper.convertValue(event.getPayload().getAdditionalProperties(), AggregationCommand.class);
                EventAggregationCriteria aggregationCriteria = objectMapper.convertValue(event.getPayload().getAdditionalProperties().get("aggregationKey"), EventAggregationCriteria.class);
                aggCommand.setAggregationKey(aggregationCriteria);
                aggregationCommands.add(aggCommand);
            }
        }

        return aggregationCommands;
    }

    @Test
    void shouldSentFourAggregationsToKafkaTopic() {

        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        dailyEmailAggregationJob.setDefaultDailyDigestTime(baseReferenceTime.toLocalTime());

        dailyEmailAggregationJob.processDailyEmail();

        List<AggregationCommand> listCommand = getRecordsFromKafka();
        assertEquals(4, listCommand.size());
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "unknown-application");
        checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application");
    }

    @Test
    void shouldSentTwoAggregationsToKafkaTopic() {
        LocalTime now = baseReferenceTime.toLocalTime();
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        dailyEmailAggregationJob.setDefaultDailyDigestTime(now);
        someOrgIdToProceed.setScheduledExecutionTime(baseReferenceTime.minusHours(2).toLocalTime());
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        // Because we added time preferences for orgId someOrgId two hours in the past, those messages must be ignored
        dailyEmailAggregationJob.processDailyEmail();

        List<AggregationCommand> listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        checkAggCommand(listCommand, "anotherOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "anotherOrgId", "rhel", "unknown-application");

        // remove all preferences, and set default hour in the past, nothing should be processed
        helpers.purgeAggregationOrgConfig();
        dailyEmailAggregationJob.setDefaultDailyDigestTime(now.minusHours(2));
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();

        dailyEmailAggregationJob.processDailyEmail();

        assertEquals(0, getRecordsFromKafka().size());

        // Finally add preferences for org id someOrgId at the right Time
        helpers.purgeAggregationOrgConfig();
        someOrgIdToProceed.setScheduledExecutionTime(dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime());
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        LocalDateTime lastRun = someOrgIdToProceed.getLastRun();
        dailyEmailAggregationJob.processDailyEmail();
        AggregationOrgConfig parameters = helpers.findAggregationOrgConfigByOrgId(someOrgIdToProceed.getOrgId());
        assertNotNull(parameters);
        assertTrue(lastRun.isBefore(parameters.getLastRun()));

        listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application");
    }

    private void checkAggCommand(List<AggregationCommand> commands, String orgId, String bundleName, String applicationName) {
        Application application = resourceHelpers.findApp(bundleName, applicationName);

        assertTrue(commands.stream().anyMatch(
            com -> orgId.equals(com.getAggregationKey().getOrgId()) &&
                application.getBundleId().equals(((EventAggregationCriteria) com.getAggregationKey()).getBundleId()) &&
                application.getId().equals(((EventAggregationCriteria) com.getAggregationKey()).getApplicationId()) &&
                DAILY.equals(com.getSubscriptionType())
            ));
    }

    @Test
    void shouldProcessOnePairRegardingExecutionTime() {
        addEventEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
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

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = dailyEmailAggregationJob.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
    void shouldProcessOnePairAtMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0);

        addEventEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId", now);
        addEventEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId", now);
        addEventEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId", now);

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

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = dailyEmailAggregationJob.getPairsProcessed();

        assertEquals(1.0, pairsProcessed.get());
    }

    @Test
    void shouldProcessFourPairs() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
        final Gauge pairsProcessed = dailyEmailAggregationJob.getPairsProcessed();

        assertEquals(4.0, pairsProcessed.get());
    }

    @Test
    void shouldProcessFivePairs() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        helpers.addAggregationOrgConfig(anotherOrgIdToProceed);

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        final Gauge pairsProcessed = dailyEmailAggregationJob.getPairsProcessed();

        assertEquals(5.0, pairsProcessed.get());
    }

    @Test
    void shouldProcessFourAggregations() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "unknown-bundle", "unknown-application", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    void shouldProcessOneAggregationOnly() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = (AggregationCommand) emailAggregations.get(0);
        assertEquals("someOrgId", aggregationCommand.getAggregationKey().getOrgId());
        Application application = resourceHelpers.findApp("rhel", "policies");
        assertEquals(application.getBundleId(), ((EventAggregationCriteria) aggregationCommand.getAggregationKey()).getBundleId());
        assertEquals(application.getId(), ((EventAggregationCriteria) aggregationCommand.getAggregationKey()).getApplicationId());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId2", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId1");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldProcessZeroAggregations() {
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }

    @Test
    void validateScheduleExecutionTimeAdjustment() {
        final LocalDateTime refTime = LocalDateTime.now(UTC).withHour(15);
        when(dailyEmailAggregationJob.computeScheduleExecutionTime()).thenCallRealMethod();

        try (MockedStatic<LocalDateTime> mockedStatic = Mockito.mockStatic(LocalDateTime.class)) {

            // should correct time to 15:00
            mockedStatic.when(() -> LocalDateTime.now(eq(UTC)))
                .thenReturn(refTime.withMinute(ThreadLocalRandom.current().nextInt(0, 14)));
            LocalDateTime adjustedTime = dailyEmailAggregationJob.computeScheduleExecutionTime();
            assertEquals(refTime.withMinute(0).withSecond(0).withNano(0), adjustedTime);

            // should correct time to 15:15
            mockedStatic.when(() -> LocalDateTime.now(eq(UTC)))
                .thenReturn(refTime.withMinute(ThreadLocalRandom.current().nextInt(15, 29)));
            adjustedTime = dailyEmailAggregationJob.computeScheduleExecutionTime();
            assertEquals(refTime.withMinute(15).withSecond(0).withNano(0), adjustedTime);

            // should correct time to 15:30
            mockedStatic.when(() -> LocalDateTime.now(eq(UTC)))
                .thenReturn(refTime.withMinute(ThreadLocalRandom.current().nextInt(30, 44)));
            adjustedTime = dailyEmailAggregationJob.computeScheduleExecutionTime();
            assertEquals(refTime.withMinute(30).withSecond(0).withNano(0), adjustedTime);

            // should correct time to 15:45
            mockedStatic.when(() -> LocalDateTime.now(eq(UTC)))
                .thenReturn(refTime.withMinute(ThreadLocalRandom.current().nextInt(45, 59)));
            adjustedTime = dailyEmailAggregationJob.computeScheduleExecutionTime();
            assertEquals(refTime.withMinute(45).withSecond(0).withNano(0), adjustedTime);
        }
    }


    private com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, String policyId, String inventoryId) {
        return addEventEmailAggregation(orgId, bundleName, applicationName, policyId, inventoryId, LocalDateTime.now(UTC).minusHours(5));
    }

    private com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, String policyId, String inventoryId, LocalDateTime created) {
        Application application = resourceHelpers.findOrCreateApplication(bundleName, applicationName);
        EventType eventType = resourceHelpers.findOrCreateEventType(application.getId(), "event_type_test");
        resourceHelpers.findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);

        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setPayload(
            TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, policyId, inventoryId).toString()
        );
        event.setCreated(created);

        return resourceHelpers.createEvent(event);
    }
}
