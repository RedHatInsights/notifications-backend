package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventAggregationCommand;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.IAggregationCommand;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyEventAggregationJobTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectSpy
    DailyEmailAggregationJob testee;

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
        when(aggregatorConfig.isAggregationBasedOnEventEnable()).thenReturn(true);
        baseReferenceTime = testee.computeScheduleExecutionTime();

        when(testee.computeScheduleExecutionTime()).thenReturn(baseReferenceTime);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    void initAggregationParameters() {
        helpers.purgeAggregationOrgConfig();
        testee.defaultDailyDigestTime = LocalTime.now(ZoneOffset.UTC);
    }

    List<EventAggregationCommand> getRecordsFromKafka() {
        List<EventAggregationCommand> aggregationCommands = new ArrayList<>();

        InMemorySink<String> results = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL);
        for (Message message : results.received()) {
            Action action = Parser.decode(String.valueOf(message.getPayload()));
            for (Event event : action.getEvents()) {
                aggregationCommands.add(objectMapper.convertValue(event.getPayload().getAdditionalProperties(), EventAggregationCommand.class));
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
        testee.setDefaultDailyDigestTime(baseReferenceTime.toLocalTime());

        testee.processDailyEmail();

        List<EventAggregationCommand> listCommand = getRecordsFromKafka();
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
        testee.setDefaultDailyDigestTime(now);
        someOrgIdToProceed.setScheduledExecutionTime(baseReferenceTime.minusHours(2).toLocalTime());
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        // Because we added time preferences for orgId someOrgId two hours in the past, those messages must me ignored
        testee.processDailyEmail();

        List<EventAggregationCommand> listCommand = getRecordsFromKafka();
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

    private void checkAggCommand(List<EventAggregationCommand> commands, String orgId, String bundleName, String applicationName) {
        Application application = resourceHelpers.findApp(bundleName, applicationName);

        assertTrue(commands.stream().anyMatch(
            com -> orgId.equals(com.getAggregationKey().getOrgId()) &&
                application.getBundleId().equals(com.getAggregationKey().getBundleId()) &&
                application.getId().equals(com.getAggregationKey().getApplicationId()) &&
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

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

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

        testee.processAggregateEmailsWithOrgPref(now, new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

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

        testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
        final Gauge pairsProcessed = testee.getPairsProcessed();

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

        testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        final Gauge pairsProcessed = testee.getPairsProcessed();

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

        final List<IAggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(4, emailAggregations.size());
    }

    @Test
    void shouldProcessOneAggregationOnly() {
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        addEventEmailAggregation("someOrgId", "rhel", "policies", "somePolicyId", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<IAggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());

        final EventAggregationCommand aggregationCommand = (EventAggregationCommand) emailAggregations.get(0);
        assertEquals("someOrgId", aggregationCommand.getAggregationKey().getOrgId());
        Application application = resourceHelpers.findApp("rhel", "policies");
        assertEquals(application.getBundleId(), aggregationCommand.getAggregationKey().getBundleId());
        assertEquals(application.getId(), aggregationCommand.getAggregationKey().getApplicationId());
        assertEquals(DAILY, aggregationCommand.getSubscriptionType());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenPolicyIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "policyId2", "someHostId");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "policyId1", "someHostId");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        final List<IAggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldNotIncreaseAggregationsWhenHostIdIsDifferent() {
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId1");
        addEventEmailAggregation("someOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        addEventEmailAggregation("shouldBeIgnoredOrgId", "some-rhel", "some-policies", "somePolicyId", "hostId2");
        helpers.addAggregationOrgConfig(someOrgIdToProceed);

        List<IAggregationCommand> emailAggregations = null;
        emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());
        assertEquals(1, emailAggregations.size());
    }

    @Test
    void shouldProcessZeroAggregations() {
        helpers.addAggregationOrgConfig(someOrgIdToProceed);
        final List<IAggregationCommand> emailAggregations = testee.processAggregateEmailsWithOrgPref(LocalDateTime.now(UTC), new CollectorRegistry());

        assertEquals(0, emailAggregations.size());
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
