package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    @InjectSpy
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    LocalDateTime baseReferenceTime;

    AggregationOrgConfig someOrgIdToProceed;

    AggregationOrgConfig anotherOrgIdToProceed;

    @BeforeEach
    void setUp() {
        someOrgIdToProceed = new AggregationOrgConfig("someOrgId",
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            dailyEmailAggregationJob.computeScheduleExecutionTime().minusDays(1));

        anotherOrgIdToProceed = new AggregationOrgConfig("anotherOrgId",
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            dailyEmailAggregationJob.computeScheduleExecutionTime().minusDays(1));

        helpers.purgeEventAggregations();
        initAggregationParameters();

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
            aggregationCommands.addAll(extractAggregationCommandsFromAction(action));
        }

        return aggregationCommands;
    }

    private List<AggregationCommand> extractAggregationCommandsFromAction(Action action) {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        for (Event event : action.getEvents()) {
            AggregationCommand aggCommand = objectMapper.convertValue(event.getPayload().getAdditionalProperties(), AggregationCommand.class);
            EventAggregationCriterion aggregationCriteria = objectMapper.convertValue(event.getPayload().getAdditionalProperties().get("aggregationKey"), EventAggregationCriterion.class);
            aggCommand.setAggregationKey(aggregationCriteria);
            aggregationCommands.add(aggCommand);
        }
        return aggregationCommands;
    }

    List<Action> getActionRecordsFromKafka() {
        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL);
        return results.received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
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
    void shouldSentFourAggregationsOnTwoBundlesToKafkaTopic() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "advisor", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "subscription-services", "errata", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "subscription-services", "errata", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "rhel", "advisor", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "subscription-services", "errata", "somePolicyId", "someHostId");
        addEventEmailAggregation("anotherOrgId", "subscription-services", "errata", "somePolicyId", "someHostId");
        dailyEmailAggregationJob.setDefaultDailyDigestTime(baseReferenceTime.toLocalTime());

        dailyEmailAggregationJob.processDailyEmail();

        List<Action> listActions = getActionRecordsFromKafka();
        assertEquals(4, listActions.size(), "Must have actions for [someOrgId/rhel], [anotherOrgId/rhel], [someOrgId/subscription-services] and [anotherOrgId/subscription-services].");

        boolean someOrgIdRhelValidated = false;
        boolean anotherOrgIdRhelValidated = false;
        boolean someOrgIdSubscriptionServicesValidated = false;
        boolean anotherOrgIdSubscriptionServicesValidated = false;

        for (Action action : listActions) {
            List<AggregationCommand> listCommand = extractAggregationCommandsFromAction(action);
            if (listCommand.getFirst().getAggregationKey().getBundle().equals("rhel")) {
                assertEquals(2, listCommand.size());
                if (action.getOrgId().equals("someOrgId")) {
                    checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
                    checkAggCommand(listCommand, "someOrgId", "rhel", "advisor");
                    someOrgIdRhelValidated = true;
                } else {
                    checkAggCommand(listCommand, "anotherOrgId", "rhel", "policies");
                    checkAggCommand(listCommand, "anotherOrgId", "rhel", "advisor");
                    anotherOrgIdRhelValidated = true;
                }
            } else {
                assertEquals(1, listCommand.size());
                if (action.getOrgId().equals("someOrgId")) {
                    checkAggCommand(listCommand, "someOrgId", "subscription-services", "errata");
                    someOrgIdSubscriptionServicesValidated = true;
                } else {
                    checkAggCommand(listCommand, "anotherOrgId", "subscription-services", "errata");
                    anotherOrgIdSubscriptionServicesValidated = true;
                }
            }
        }

        assertTrue(someOrgIdRhelValidated);
        assertTrue(anotherOrgIdRhelValidated);
        assertTrue(someOrgIdSubscriptionServicesValidated);
        assertTrue(anotherOrgIdSubscriptionServicesValidated);
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
        AggregationOrgConfig parameters = helpers.findDailyAggregationOrgConfigByOrgId(someOrgIdToProceed.getOrgId());
        assertNotNull(parameters);
        assertTrue(lastRun.isBefore(parameters.getLastRun()));

        listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        checkAggCommand(listCommand, "someOrgId", "rhel", "policies");
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application");
    }

    @Test
    void shouldNotStartBeforeThanTwoDays() {
        LocalTime now = baseReferenceTime.toLocalTime();
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        dailyEmailAggregationJob.setDefaultDailyDigestTime(now);
        someOrgIdToProceed.setScheduledExecutionTime(baseReferenceTime.toLocalTime());
        someOrgIdToProceed.setLastRun(baseReferenceTime.minusDays(5));
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        dailyEmailAggregationJob.processDailyEmail();

        List<AggregationCommand> listCommand = getRecordsFromKafka();
        assertEquals(2, listCommand.size());

        final LocalDateTime expectedStartTime = LocalDateTime.now(UTC)
            .withHour(baseReferenceTime.getHour())
            .withMinute(baseReferenceTime.getMinute())
            .withSecond(baseReferenceTime.getSecond())
            .withNano(baseReferenceTime.getNano())
            .minusDays(2);

        checkAggCommand(listCommand, "someOrgId", "rhel", "policies", expectedStartTime);
        checkAggCommand(listCommand, "someOrgId", "rhel", "unknown-application", expectedStartTime);
    }

    private void checkAggCommand(final List<AggregationCommand> commands, final String orgId, final String bundleName, final String applicationName, final LocalDateTime expectedStartDate) {
        final Application application = helpers.findApp(bundleName, applicationName);

        final LocalDateTime expectedEndDate = LocalDateTime.now(UTC)
            .withHour(baseReferenceTime.getHour())
            .withMinute(baseReferenceTime.getMinute())
            .withSecond(baseReferenceTime.getSecond())
            .withNano(baseReferenceTime.getNano());

        assertTrue(commands.stream().anyMatch(
            com -> orgId.equals(com.getAggregationKey().getOrgId()) &&
                application.getBundleId().equals(((EventAggregationCriterion) com.getAggregationKey()).getBundleId()) &&
                application.getId().equals(((EventAggregationCriterion) com.getAggregationKey()).getApplicationId()) &&
                DAILY.equals(com.getSubscriptionType()) &&
                com.getStart().isEqual(expectedStartDate) &&
                com.getEnd().isEqual(expectedEndDate)
        ));
    }

    private void checkAggCommand(final List<AggregationCommand> commands, final String orgId, final String bundleName, final String applicationName) {
        final LocalDateTime expectedStartTime = LocalDateTime.now(UTC)
            .withHour(baseReferenceTime.getHour())
            .withMinute(baseReferenceTime.getMinute())
            .withSecond(baseReferenceTime.getSecond())
            .withNano(baseReferenceTime.getNano())
            .minusDays(1);
        checkAggCommand(commands, orgId, bundleName, applicationName, expectedStartTime);
    }

    @Test
    void shouldProcessOnePairRegardingExecutionTime() {
        addEventEmailAggregation("tooLateOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("onTimeOrgId", "rhel", "unknown-application", "somePolicyId", "someHostId");
        addEventEmailAggregation("tooSoonOrgId", "unknown-bundle", "policies", "somePolicyId", "someHostId");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withMinute(30).withSecond(0).withNano(0);

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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0);

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

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());
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

        dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

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

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    void shouldProcessOneAggregationOnly() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = (AggregationCommand) emailAggregations.get(0);
        assertEquals("someOrgId", aggregationCommand.getAggregationKey().getOrgId());
        Application application = helpers.findApp("rhel", "policies");
        assertEquals(application.getBundleId(), ((EventAggregationCriterion) aggregationCommand.getAggregationKey()).getBundleId());
        assertEquals(application.getId(), ((EventAggregationCriterion) aggregationCommand.getAggregationKey()).getApplicationId());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldProcessOneAggregationOnlyWithoutLastRunDate() {
        addEventEmailAggregation("someOrgIdWithoutLastRunDate", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgIdWithoutLastRunDate", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(new AggregationOrgConfig("someOrgIdWithoutLastRunDate",
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            null));

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final AggregationCommand aggregationCommand = (AggregationCommand) emailAggregations.get(0);
        assertEquals("someOrgIdWithoutLastRunDate", aggregationCommand.getAggregationKey().getOrgId());
        Application application = helpers.findApp("rhel", "policies");
        assertEquals(application.getBundleId(), ((EventAggregationCriterion) aggregationCommand.getAggregationKey()).getBundleId());
        assertEquals(application.getId(), ((EventAggregationCriterion) aggregationCommand.getAggregationKey()).getApplicationId());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId2", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId1");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());
        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldProcessZeroAggregations() {
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
    }

    @Test
    void shouldSkipAggregationsWhenNoSubscribersExist() {
        // Create events without subscriptions
        String orgId = "noSubscriberOrgId";
        String bundleName = "test-bundle";
        String applicationName = "test-app";

        // Create the infrastructure (bundle, app, event type) but no subscription
        helpers.findOrCreateBundle(bundleName);
        Application application = helpers.findOrCreateApplication(bundleName, applicationName);
        EventType eventType = helpers.findOrCreateEventType(application.getId(), "event_type_no_subscription");

        // Create email endpoint linked to event type (required by query)
        helpers.getOrCreateEmailEndpointAndLinkItToEventType(orgId, eventType, false);

        // Create events WITHOUT creating subscriptions
        com.redhat.cloud.notifications.models.Event event1 = new com.redhat.cloud.notifications.models.Event();
        event1.setOrgId(orgId);
        eventType.setApplication(application);
        event1.setEventType(eventType);
        event1.setCreated(LocalDateTime.now(UTC).minusHours(5));
        event1.setPayload("{\"test\":\"payload1\"}");
        helpers.createEvent(event1);

        com.redhat.cloud.notifications.models.Event event2 = new com.redhat.cloud.notifications.models.Event();
        event2.setOrgId(orgId);
        eventType.setApplication(application);
        event2.setEventType(eventType);
        event2.setCreated(LocalDateTime.now(UTC).minusHours(3));
        event2.setPayload("{\"test\":\"payload2\"}");
        helpers.createEvent(event2);

        // Add org config to ensure the org would be processed if subscribers existed
        AggregationOrgConfig noSubscriberOrgConfig = new AggregationOrgConfig(orgId,
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            dailyEmailAggregationJob.computeScheduleExecutionTime().minusDays(1));
        helpers.addAggregationOrgConfig(noSubscriberOrgConfig);

        // Process aggregations
        final List<AggregationCommand> emailAggregations = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(
            dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        // Verify that 0 aggregation commands are returned since no subscribers exist
        assertEquals(0, emailAggregations.size(), "Aggregations should be skipped when no subscribers exist");
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


    @Test
    void shouldIgnoreWeeklyConfigRowsDuringDailyAggregation() {
        addEventEmailAggregation("weeklyTestOrgId", "rhel", "policies", "somePolicyId", "someHostId");

        // add a DAILY config at the right time
        AggregationOrgConfig dailyConfig = new AggregationOrgConfig("weeklyTestOrgId",
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            dailyEmailAggregationJob.computeScheduleExecutionTime().minusDays(1));
        helpers.addAggregationOrgConfig(dailyConfig);

        // add a WEEKLY config for the same org (should be ignored by the daily job)
        AggregationOrgConfig weeklyConfig = new AggregationOrgConfig("weeklyTestOrgId",
            SubscriptionType.WEEKLY,
            dailyEmailAggregationJob.computeScheduleExecutionTime().toLocalTime(),
            DayOfWeek.MONDAY);
        weeklyConfig.setLastRun(dailyEmailAggregationJob.computeScheduleExecutionTime().minusDays(7));
        helpers.addAggregationOrgConfig(weeklyConfig);

        List<AggregationCommand> commands = dailyEmailAggregationJob.processAggregateEmailsWithOrgPref(
            dailyEmailAggregationJob.computeScheduleExecutionTime(), new CollectorRegistry());

        // should produce exactly 1 aggregation (from the DAILY row only, not duplicated by the WEEKLY row)
        assertEquals(1, commands.size());
        assertEquals(DAILY, commands.get(0).getSubscriptionType());
    }

    private com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, String policyId, String inventoryId) {
        return addEventEmailAggregation(orgId, bundleName, applicationName, policyId, inventoryId, LocalDateTime.now(UTC).minusHours(5));
    }

    private com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, String policyId, String inventoryId, LocalDateTime created) {
        final String payload = TestHelpers.generatePayloadContent(orgId, bundleName, applicationName, policyId, inventoryId).toString();
        return helpers.addEventEmailAggregation(orgId, bundleName, applicationName, created, payload, false);
    }
}
