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
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.PayloadDetails;
import com.redhat.cloud.notifications.processors.email.EmailAggregator;
import com.redhat.cloud.notifications.recipients.User;
import io.prometheus.client.CollectorRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
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

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The daily job with the REAL enricher (only the folding and the resolver-backed aggregation are mocked): the trigger of
 * the flagged org carries entries that point at stored payloads, the trigger of the other org does not.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DailyAggregatedPayloadsEndToEndTest {

    private static final String FLAGGED_ORG = "e2eFlaggedOrg";
    private static final String OTHER_ORG = "e2eOtherOrg";

    @Inject
    ResourceHelpers helpers;

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Any
    InMemoryConnector connector;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    @InjectMock
    EmailAggregator emailAggregator;

    LocalDateTime referenceTime;

    @BeforeEach
    void setUp() {
        helpers.purgeEventAggregations();
        helpers.purgeAggregationOrgConfig();
        helpers.purgeEndpoints();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
        referenceTime = AggregationTimeHelper.computeReferenceTime();
        when(aggregatorConfig.getDefaultDailyDigestTime()).thenReturn(referenceTime.toLocalTime());
        when(aggregatorConfig.getRhHccSender()).thenReturn("sender@example.com");
        when(aggregatorConfig.isNewAggregationPipelineEnabled(FLAGGED_ORG)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEventAggregations();
        helpers.purgeAggregationOrgConfig();
        helpers.purgeEndpoints();
        connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).clear();
    }

    @Test
    void flaggedOrgTriggerCarriesEntriesPointingAtStoredPayloads() throws Exception {
        Application policies = helpers.findOrCreateApplication("rhel", "policies");
        EventType eventType = helpers.findOrCreateEventType(policies.getId(), "event_type_test");
        for (String orgId : List.of(FLAGGED_ORG, OTHER_ORG)) {
            String payload = TestHelpers.generatePayloadContent(orgId, "rhel", "policies", "policyId", "hostId").toString();
            helpers.addEventEmailAggregation(orgId, "rhel", "policies", LocalDateTime.now(UTC).minusHours(5), payload, false);
        }
        Endpoint endpoint = helpers.createReadyEmailEndpoint(FLAGGED_ORG, eventType, false);
        when(emailAggregator.getAggregated(eq(policies.getId()), any(), any(), any(), any())).thenReturn(Map.of(
            user("alice"), Map.of("policies", Map.of("count", 1))));

        dailyEmailAggregationJob.searchBuildAndSendAggregationCommands(referenceTime, new CollectorRegistry());

        List<Action> actions = connector.sink(DailyEmailAggregationJob.EGRESS_CHANNEL).received().stream()
            .map(message -> Parser.decode(String.valueOf(message.getPayload())))
            .toList();
        assertEquals(2, actions.size());
        for (Action action : actions) {
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
