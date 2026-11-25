package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.models.AggregatedPayloadEntry;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.PayloadDetails;
import com.redhat.cloud.notifications.processors.email.AggregatedPayloadBuilder;
import com.redhat.cloud.notifications.processors.email.EmailAggregator;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class AggregatedPayloadBuilderTest {

    private static final String ORG_ID = "enricherOrg";

    // The keys the email connector reads from the stored contents (its EmailNotification record plus org_id/endpoint_id)
    private static final Set<String> EXPECTED_CONTENT_KEYS = Set.of(
        "email_sender", "org_id", "recipient_settings", "subscribers", "unsubscribers", "subscribed_by_default",
        "recipients_authorization_criterion", "event_data", "id_daily_digest", "subscription_type", "endpoint_id");

    @Inject
    ResourceHelpers helpers;

    @Inject
    AggregatedPayloadBuilder aggregatedPayloadBuilder;

    @Inject
    EntityManager entityManager;

    @InjectMock
    EmailAggregator emailAggregator;

    @InjectMock
    AggregatorConfig aggregatorConfig;

    Application advisor;
    EventType eventType;
    Endpoint endpoint;
    AggregationCommand command;

    @BeforeEach
    void setUp() {
        helpers.purgeEventAggregations();
        helpers.purgeEndpoints();
        when(aggregatorConfig.getRhHccSender()).thenReturn("sender@example.com");
        advisor = helpers.findOrCreateApplication("rhel", "advisor");
        eventType = helpers.findOrCreateEventType(advisor.getId(), "enricher-event-type");
        endpoint = helpers.createReadyEmailEndpoint(ORG_ID, eventType, false);
        command = new AggregationCommand(
            new EventAggregationCriterion(ORG_ID, advisor.getBundleId(), advisor.getId(), "rhel", "advisor"),
            LocalDateTime.now(UTC).minusHours(3), LocalDateTime.now(UTC), DAILY);
    }

    @AfterEach
    void tearDown() {
        // Deleting the events cascades to the payload_details rows anchored to them.
        helpers.purgeEventAggregations();
        helpers.purgeEndpoints();
    }

    @Test
    void savesOneRowPerGroupAnchoredToAnEventAndReturnsAMatchingEntry() {
        Event windowEvent = addWindowEvent();
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenReturn(Map.of(
            user("alice"), Map.of("advisor", Map.of("rule", "a")),
            user("bob"), Map.of("advisor", Map.of("rule", "b"))));

        List<Map<String, Object>> entries = aggregatedPayloadBuilder.build(List.of(command));

        assertEquals(2, entries.size());
        for (Map<String, Object> entryMap : entries) {
            AggregatedPayloadEntry entry = new AggregatedPayloadEntry(
                UUID.fromString((String) entryMap.get("payload_details_id")), UUID.fromString((String) entryMap.get("endpoint_id")));
            assertEquals(endpoint.getId(), entry.endpointId());

            PayloadDetails stored = entityManager.find(PayloadDetails.class, entry.payloadDetailsId());
            assertEquals(windowEvent.getId(), stored.getEventId());
            JsonObject contents = new JsonObject(stored.getContents());
            assertEquals(EXPECTED_CONTENT_KEYS, contents.fieldNames());
            assertEquals(ORG_ID, contents.getString("org_id"));
            assertEquals(endpoint.getId().toString(), contents.getString("endpoint_id"));
            assertEquals("sender@example.com", contents.getString("email_sender"));
            assertEquals("DAILY", contents.getString("subscription_type"));
            assertEquals(true, contents.getBoolean("id_daily_digest"));
            assertEquals(1, contents.getJsonArray("subscribers").size());
        }
        assertEquals(Set.of("alice", "bob"), usernamesOf(entries));
    }

    @Test
    void nothingToSendSavesNothingAndReturnsNoEntries() {
        addWindowEvent();
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenReturn(Map.of());
        long rowsBefore = countRows();

        assertTrue(aggregatedPayloadBuilder.build(List.of(command)).isEmpty());
        assertEquals(rowsBefore, countRows());
    }

    @Test
    void aFailureWhileAggregatingSavesNothing() {
        addWindowEvent();
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("simulated failure"));
        long rowsBefore = countRows();

        assertThrows(IllegalStateException.class, () -> aggregatedPayloadBuilder.build(List.of(command)));
        assertEquals(rowsBefore, countRows());
    }

    @Test
    void withoutAnEventToAnchorTheRowsItFailsAndSavesNothing() {
        when(emailAggregator.getAggregated(any(), any(), any(), any(), any())).thenReturn(Map.of(user("alice"), Map.of("advisor", Map.of())));
        long rowsBefore = countRows();

        assertThrows(IllegalStateException.class, () -> aggregatedPayloadBuilder.build(List.of(command)));
        assertEquals(rowsBefore, countRows());
    }

    private Set<String> usernamesOf(List<Map<String, Object>> entries) {
        Set<String> usernames = new java.util.HashSet<>();
        for (Map<String, Object> entry : entries) {
            PayloadDetails stored = entityManager.find(PayloadDetails.class, UUID.fromString((String) entry.get("payload_details_id")));
            new JsonObject(stored.getContents()).getJsonArray("subscribers").forEach(subscriber -> usernames.add((String) subscriber));
        }
        return usernames;
    }

    private long countRows() {
        return entityManager.createQuery("SELECT COUNT(p) FROM PayloadDetails p", Long.class).getSingleResult();
    }

    private Event addWindowEvent() {
        String payload = TestHelpers.generatePayloadContent(ORG_ID, "rhel", "advisor", "policy", "host").toString();
        return helpers.addEventEmailAggregation(ORG_ID, "rhel", "advisor", LocalDateTime.now(UTC).minusHours(1), payload, false);
    }

    private static User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }
}
