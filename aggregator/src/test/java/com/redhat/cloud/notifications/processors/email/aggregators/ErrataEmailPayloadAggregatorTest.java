package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_BUGFIX;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_ENHANCEMENT;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_SECURITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrataEmailPayloadAggregatorTest {

    private static final String ORG_ID = "test-org-id";

    private ErrataEmailPayloadAggregator aggregator;

    @BeforeEach
    void initTest() {
        aggregator = new ErrataEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void aggregatorTests() {
        aggregateEventType("", 1);
        assertEquals(ORG_ID, aggregator.getOrgId());

        Map<String, Object> context = aggregator.getContext();
        JsonObject errata = JsonObject.mapFrom(context).getJsonObject("errata");

        assertEquals(0, errata.getJsonArray(EVENT_TYPE_BUGFIX).size());
        assertEquals(0, errata.getJsonArray(EVENT_TYPE_ENHANCEMENT).size());
        assertEquals(0, errata.getJsonArray(EVENT_TYPE_SECURITY).size());

        aggregateEventType(EVENT_TYPE_BUGFIX, 3);
        aggregateEventType(EVENT_TYPE_ENHANCEMENT, 2);
        aggregateEventType(EVENT_TYPE_SECURITY, 4);

        context = aggregator.getContext();
        errata = JsonObject.mapFrom(context).getJsonObject("errata");

        // each errata action have 3 events, we are checking total aggregated events
        assertEquals(3 * 3, errata.getJsonArray(EVENT_TYPE_BUGFIX).size());
        assertEquals(2 * 3, errata.getJsonArray(EVENT_TYPE_ENHANCEMENT).size());
        assertEquals(4 * 3, errata.getJsonArray(EVENT_TYPE_SECURITY).size());
    }

    @Test
    void shouldTagEachItemWithItsOwnSeverity() {
        aggregator.aggregate(createErrataAggregation(EVENT_TYPE_SECURITY, Severity.IMPORTANT));

        JsonArray security = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("errata")
            .getJsonArray(EVENT_TYPE_SECURITY);

        assertEquals("IMPORTANT", security.getJsonObject(0).getString("_severity"));
    }

    @Test
    void shouldTagEachItemWithFullMetadata() {
        EmailAggregation aggregation = createErrataAggregation(EVENT_TYPE_SECURITY, Severity.IMPORTANT);
        aggregation.setSubscribedByDefault(true);
        aggregation.setRecipientsOverride(new JsonArray().add(new JsonObject().put("only_admins", true)));

        aggregator.aggregate(aggregation);

        JsonObject item = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("errata")
            .getJsonArray(EVENT_TYPE_SECURITY)
            .getJsonObject(0);

        assertEquals(EVENT_TYPE_SECURITY, item.getString("_eventType"));
        assertTrue(item.getBoolean("_subscribedByDefault"));
        assertEquals(1, item.getJsonArray("_recipientsOverride").size());
    }

    @Test
    void shouldNotTagSeverityWhenSeverityIsNull() {
        aggregator.aggregate(createErrataAggregation(EVENT_TYPE_SECURITY, null));

        JsonArray security = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("errata")
            .getJsonArray(EVENT_TYPE_SECURITY);

        assertEquals(3, security.size(), "the items should still be aggregated, just without a severity tag");
        assertTrue(security.stream().noneMatch(item -> ((JsonObject) item).containsKey("_severity")));
    }

    @Test
    void shouldSkipEventWithNoPayload() {
        JsonObject context = new JsonObject().put("base_url", "https://access.redhat.com");
        JsonObject action = new JsonObject()
            .put("event_type", EVENT_TYPE_SECURITY)
            .put("context", context)
            .put("events", new JsonArray().add(new JsonObject()));

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "subscription-services", action, Severity.IMPORTANT, UUID.randomUUID());
        aggregation.setEventTypeName(EVENT_TYPE_SECURITY);

        aggregator.aggregate(aggregation);

        JsonArray security = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("errata")
            .getJsonArray(EVENT_TYPE_SECURITY);

        assertTrue(security.isEmpty(), "an event with no payload must be skipped, not crash the fold");
    }

    private void aggregateEventType(final String eventType, final int numberOfAggregations) {
        for (int i = 0; i < numberOfAggregations; i++) {
            aggregator.aggregate(createErrataAggregation(eventType, Severity.MODERATE));
        }
    }

    private static EmailAggregation createErrataAggregation(String eventType, Severity severity) {
        JsonObject context = new JsonObject().put("base_url", "https://access.redhat.com");
        JsonArray events = new JsonArray();
        for (int i = 0; i < 3; i++) {
            events.add(new JsonObject().put("payload", new JsonObject().put("advisory_name", "RHSA-2024:" + i)));
        }
        JsonObject action = new JsonObject()
            .put("event_type", eventType)
            .put("context", context)
            .put("events", events);

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "subscription-services", action, severity, UUID.randomUUID());
        aggregation.setEventTypeName(eventType);
        return aggregation;
    }
}
