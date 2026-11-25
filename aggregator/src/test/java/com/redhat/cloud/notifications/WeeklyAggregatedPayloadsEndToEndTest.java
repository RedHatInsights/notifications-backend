package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.AggregationTimeHelper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.AggregatedPayloadEntry;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.PayloadDetails;
import com.redhat.cloud.notifications.processors.email.EmailAggregator;
import com.redhat.cloud.notifications.recipients.User;
import io.prometheus.client.CollectorRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * The weekly job with the REAL enricher (only the folding and the resolver-backed aggregation are mocked), the weekly
 * counterpart of {@link DailyAggregatedPayloadsEndToEndTest}.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WeeklyAggregatedPayloadsEndToEndTest {

    private static final String DEFAULT_WEEKLY_CRON = "30 0 * * 1";
    private static final String FLAGGED_ORG = "weeklyE2eFlaggedOrg";
    private static final String OTHER_ORG = "weeklyE2eOtherOrg";

    @Inject
    ResourceHelpers helpers;

    @Inject
    WeeklyEmailAggregationJob weeklyEmailAggregationJob;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Any
    InMemoryConnector connector;

    @InjectSpy
    AggregatorConfig aggregatorConfig;

    @InjectMock
    EmailAggregator emailAggregator;

    LocalDateTime now;
    LocalDateTime executionTime;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(UTC);
        executionTime = AggregationTimeHelper.computeReferenceTime(now);
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
        helpers.purgeEndpoints();
        connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).clear();
        doReturn(true).when(aggregatorConfig).isNewAggregationPipelineEnabled(FLAGGED_ORG);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        helpers.purgeDigestTriggerOrgConfig();
        helpers.purgeEndpoints();
        connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @Test
    void flaggedOrgTriggerCarriesEntriesPointingAtStoredWeeklyPayloads() throws Exception {
        Application policies = helpers.findOrCreateApplication("rhel", "policies");
        EventType eventType = helpers.findOrCreateEventType(policies.getId(), "event_type_test");
        for (String orgId : List.of(FLAGGED_ORG, OTHER_ORG)) {
            DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(new DigestTriggerOrgConfigId(orgId, WEEKLY), DEFAULT_WEEKLY_CRON);
            // The weekly window ends at the last cron slot (depends on the weekday), so the events are well inside it:
            // two weeks ago, with the window starting three weeks ago.
            config.setLastRun(now.minusWeeks(3));
            config.setNextRun(executionTime);
            helpers.addDigestTriggerOrgConfig(config);
            String payload = TestHelpers.generatePayloadContent(orgId, "rhel", "policies", "policyId", "hostId").toString();
            helpers.addEventEmailAggregationWithSubscriptionType(orgId, "rhel", "policies", now.minusWeeks(2), payload, true, WEEKLY);
        }
        Endpoint endpoint = helpers.createReadyEmailEndpoint(FLAGGED_ORG, eventType, false);
        when(emailAggregator.getAggregated(eq(policies.getId()), any(), eq(WEEKLY), any(), any())).thenReturn(Map.of(
            user("alice"), Map.of("policies", Map.of("count", 1))));

        weeklyEmailAggregationJob.searchBuildAndSendAggregationCommands(executionTime, new CollectorRegistry());

        List<Action> actions = connector.sink(WeeklyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
        assertEquals(2, actions.size());
        for (Action action : actions) {
            assertEquals(WeeklyEmailAggregationJob.EVENT_TYPE_NAME, action.getEventType());
            Map<String, Object> contextProperties = action.getContext().getAdditionalProperties();
            if (!FLAGGED_ORG.equals(action.getOrgId())) {
                assertFalse(contextProperties.containsKey(AggregatedPayloadEntry.CONTEXT_KEY));
                continue;
            }
            List<AggregatedPayloadEntry> entries = objectMapper.convertValue(
                contextProperties.get(AggregatedPayloadEntry.CONTEXT_KEY), new TypeReference<List<AggregatedPayloadEntry>>() { });
            assertEquals(1, entries.size());
            assertEquals(endpoint.getId(), entries.getFirst().endpointId());
            PayloadDetails stored = entityManager.find(PayloadDetails.class, entries.getFirst().payloadDetailsId());
            assertNotNull(stored);
            JsonObject contents = new JsonObject(stored.getContents());
            assertEquals(FLAGGED_ORG, contents.getString("org_id"));
            assertEquals("WEEKLY", contents.getString("subscription_type"));
            assertEquals(List.of("alice"), contents.getJsonArray("subscribers").getList());
        }
    }

    private static User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }
}
