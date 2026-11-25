package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.email.AggregatedDigestBuilder;
import com.redhat.cloud.notifications.processors.email.EmailAggregator;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class AggregatedDigestBuilderTest {

    private static final String ORG_ID = "builderOrg";
    private static final String SENDER = "\"Test Sender\" sender@example.com";

    @Inject
    ResourceHelpers helpers;

    @Inject
    AggregatedDigestBuilder aggregatedDigestBuilder;

    @Inject
    Environment environment;

    @InjectMock
    EmailAggregator emailAggregator;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    Application advisor;
    Application policies;
    EventType advisorEventType;
    EventType policiesEventType;

    @BeforeEach
    void setUp() {
        helpers.purgeEndpoints();
        advisor = helpers.findOrCreateApplication("rhel", "advisor");
        policies = helpers.findOrCreateApplication("rhel", "policies");
        advisorEventType = helpers.findOrCreateEventType(advisor.getId(), "builder-event-type");
        policiesEventType = helpers.findOrCreateEventType(policies.getId(), "builder-event-type");
        when(aggregatorConfig.getRhHccSender()).thenReturn(SENDER);
    }

    @AfterEach
    void tearDown() {
        helpers.purgeEndpoints();
    }

    @Test
    void usersWithIdenticalDataShareOneNotificationAndOthersGetTheirOwn() {
        helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        Map<String, Object> sharedData = Map.of("advisor", Map.of("new_recommendations", Map.of("rule-1", "x")));
        Map<String, Object> otherData = Map.of("advisor", Map.of("new_recommendations", Map.of("rule-2", "y")));
        when(emailAggregator.getAggregated(eq(advisor.getId()), any(), eq(DAILY), any(), any())).thenReturn(Map.of(
            user("alice"), sharedData,
            user("bob"), sharedData,
            user("carol"), otherData));

        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(List.of(command(advisor, DAILY)));

        assertEquals(2, digests.notifications().size());
        Set<Set<String>> recipientGroups = digests.notifications().stream()
            .map(notification -> Set.copyOf(notification.subscribers()))
            .collect(Collectors.toSet());
        assertEquals(Set.of(Set.of("alice", "bob"), Set.of("carol")), recipientGroups);
    }

    @Test
    void notificationHasTheFieldsEngineSetsForAnAggregatedEmail() {
        Endpoint endpoint = helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, true);
        when(emailAggregator.getAggregated(eq(advisor.getId()), any(), eq(WEEKLY), any(), any())).thenReturn(Map.of(
            user("alice"), Map.of("advisor", Map.of("new_recommendations", Map.of("rule-1", "x")))));

        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(List.of(command(advisor, WEEKLY)));

        assertEquals(endpoint.getId(), digests.endpoint().getId());
        assertEquals(1, digests.notifications().size());
        EmailNotification notification = digests.notifications().getFirst();
        assertEquals(SENDER, notification.emailSender());
        assertEquals(ORG_ID, notification.orgId());
        assertEquals(Set.of("alice"), Set.copyOf(notification.subscribers()));
        assertTrue(notification.unsubscribers().isEmpty());
        assertFalse(notification.subscribedByDefault());
        assertNull(notification.recipientsAuthorizationCriterion());
        assertTrue(notification.isDailyDigest());
        assertEquals(SubscriptionType.WEEKLY, notification.subscriptionType());
        assertEquals(1, notification.recipientSettings().size());
        assertTrue(notification.recipientSettings().iterator().next().isAdminsOnly(), "The settings come from the endpoint properties");
    }

    @Test
    void templateContextHasTheShapeTheEmailConnectorExpects() {
        helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        helpers.createReadyEmailEndpoint(ORG_ID, policiesEventType, false);
        Map<String, Object> advisorData = Map.of("advisor", Map.of("new_recommendations", Map.of("rule-1", "x")));
        Map<String, Object> policiesData = Map.of("policies", Map.of("count", 3));
        when(emailAggregator.getAggregated(eq(advisor.getId()), any(), any(), any(), any())).thenReturn(Map.of(user("alice"), advisorData));
        when(emailAggregator.getAggregated(eq(policies.getId()), any(), any(), any(), any())).thenReturn(Map.of(user("alice"), policiesData));

        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(List.of(command(advisor, DAILY), command(policies, DAILY)));

        assertEquals(1, digests.notifications().size());
        Map<String, Object> context = digests.notifications().getFirst().eventData();
        assertEquals(Set.of("environment", "bundle_name", "bundle_display_name", "application_aggregated_data_list"), context.keySet());
        assertEquals(environment.name(), ((Environment) context.get("environment")).name());
        assertEquals("rhel", context.get("bundle_name"));
        assertNotNull(context.get("bundle_display_name"));
        JsonArray applications = (JsonArray) context.get("application_aggregated_data_list");
        assertEquals(2, applications.size());
        Map<String, JsonObject> byApplication = applications.stream()
            .map(JsonObject.class::cast)
            .collect(Collectors.toMap(application -> application.getString("app_name"), application -> application));
        assertEquals(Set.of("advisor", "policies"), byApplication.keySet());
        assertEquals(new JsonObject(advisorData), byApplication.get("advisor").getJsonObject("aggregated_data"));
        assertEquals(new JsonObject(policiesData), byApplication.get("policies").getJsonObject("aggregated_data"));
    }

    @Test
    void noEndpointMeansNothingToSendAndNothingIsAggregated() {
        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(List.of(command(advisor, DAILY)));

        assertTrue(digests.notifications().isEmpty());
        assertNull(digests.endpoint());
        verifyNoInteractions(emailAggregator);
    }

    @Test
    void nobodyToSendToMeansNoNotifications() {
        helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenReturn(Map.of());

        assertTrue(aggregatedDigestBuilder.build(List.of(command(advisor, DAILY))).notifications().isEmpty());
    }

    @Test
    void blacklistedEndpointIsDropped() {
        Endpoint endpoint = helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        when(aggregatorConfig.isBlacklistedEndpoint(endpoint.getId())).thenReturn(true);

        assertTrue(aggregatedDigestBuilder.build(List.of(command(advisor, DAILY))).notifications().isEmpty());
        verifyNoInteractions(emailAggregator);
    }

    /*
     * A system endpoint has no org in the database: the repository rewrites its orgId to the org being processed BEFORE
     * the blacklist check, so, unlike what the engine's comment says, system endpoints are checked too.
     */
    @Test
    void blacklistedSystemEndpointIsDroppedToo() {
        Endpoint systemEndpoint = helpers.createReadyEmailEndpoint(null, advisorEventType, false);
        when(aggregatorConfig.isBlacklistedEndpoint(systemEndpoint.getId())).thenReturn(true);

        assertTrue(aggregatedDigestBuilder.build(List.of(command(advisor, DAILY))).notifications().isEmpty());
        verifyNoInteractions(emailAggregator);
    }

    @Test
    void systemEndpointIsUsedWithTheOrgOfTheCommands() {
        Endpoint systemEndpoint = helpers.createReadyEmailEndpoint(null, advisorEventType, false);
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenReturn(Map.of(user("alice"), Map.of("advisor", Map.of())));

        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(List.of(command(advisor, DAILY)));

        assertEquals(systemEndpoint.getId(), digests.endpoint().getId());
        assertEquals(ORG_ID, digests.endpoint().getOrgId());
        assertEquals(1, digests.notifications().size());
    }

    @Test
    void aFailingApplicationAbortsTheWholeBuild() {
        helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        helpers.createReadyEmailEndpoint(ORG_ID, policiesEventType, false);
        when(emailAggregator.getAggregated(eq(advisor.getId()), any(), any(), any(), any())).thenReturn(Map.of(user("alice"), Map.of("advisor", Map.of())));
        when(emailAggregator.getAggregated(eq(policies.getId()), any(), any(), any(), any())).thenThrow(new IllegalStateException("simulated failure"));

        assertThrows(IllegalStateException.class, () -> aggregatedDigestBuilder.build(List.of(command(advisor, DAILY), command(policies, DAILY))));

        verify(emailAggregator, times(1)).getAggregated(eq(advisor.getId()), any(), any(), any(), any());
        verify(emailAggregator, times(1)).getAggregated(eq(policies.getId()), any(), any(), any(), any());
    }

    @Test
    void unknownBundleFails() {
        helpers.createReadyEmailEndpoint(ORG_ID, advisorEventType, false);
        AggregationCommand command = new AggregationCommand(
            new EventAggregationCriterion(ORG_ID, advisor.getBundleId(), advisor.getId(), "unknown-bundle", "advisor"),
            LocalDateTime.now(UTC).minusDays(1), LocalDateTime.now(UTC), DAILY);

        assertThrows(IllegalArgumentException.class, () -> aggregatedDigestBuilder.build(List.of(command)));
        verify(emailAggregator, never()).getAggregated(any(), any(), any(), any(), any());
    }

    private AggregationCommand command(Application application, SubscriptionType subscriptionType) {
        return new AggregationCommand(
            new EventAggregationCriterion(ORG_ID, application.getBundleId(), application.getId(), "rhel", application.getName()),
            LocalDateTime.now(UTC).minusDays(1), LocalDateTime.now(UTC), subscriptionType);
    }

    private static User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }
}
