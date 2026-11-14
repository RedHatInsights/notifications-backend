package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class ComplianceEmailAggregatorTest {

    private static final String ORG_ID = "test-org-id";

    private ComplianceEmailAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ComplianceEmailAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policyId", "inventoryId", Severity.CRITICAL));
        Assertions.assertEquals(ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        aggregator.aggregate(createEmailAggregation("foo", "policy0", "host0", Severity.CRITICAL));
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy1", "host1", Severity.CRITICAL));
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy2", "host2", Severity.LOW));
        aggregator.aggregate(createEmailAggregation("compliance-below-threshold", "policy3", "host3", Severity.CRITICAL));
        aggregator.aggregate(createEmailAggregation("compliance-below-threshold", "policy4", "host4", Severity.LOW));

        Map<String, Object> context = aggregator.getContext();
        JsonObject compliance = JsonObject.mapFrom(context).getJsonObject("compliance");

        Assertions.assertFalse(compliance.containsKey("foo"));
        Assertions.assertEquals(2, compliance.getJsonArray("report-upload-failed").size());
        Assertions.assertEquals(2, compliance.getJsonArray("compliance-below-threshold").size());
    }

    @Test
    void shouldTagEachItemWithItsOwnSeverity() {
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy1", "host1", Severity.CRITICAL));
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy2", "host2", Severity.LOW));

        JsonArray reportUploadFailed = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed");

        Assertions.assertEquals("CRITICAL", reportUploadFailed.getJsonObject(0).getString("_severity"));
        Assertions.assertEquals("LOW", reportUploadFailed.getJsonObject(1).getString("_severity"));
    }

    @Test
    void shouldTagEachItemWithFullMetadata() {
        EmailAggregation aggregation = createEmailAggregation("report-upload-failed", "policy1", "host1", Severity.CRITICAL);
        aggregation.setSubscribedByDefault(true);
        aggregation.setRecipientsOverride(new JsonArray().add(new JsonObject().put("only_admins", true)));

        aggregator.aggregate(aggregation);

        JsonObject item = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed")
            .getJsonObject(0);

        Assertions.assertEquals("report-upload-failed", item.getString("_eventType"));
        Assertions.assertTrue(item.getBoolean("_subscribedByDefault"));
        Assertions.assertEquals(1, item.getJsonArray("_recipientsOverride").size());
    }

    @Test
    void shouldNotTagRecipientsOverrideWhenAbsent() {
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy1", "host1", Severity.CRITICAL));

        JsonObject item = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed")
            .getJsonObject(0);

        Assertions.assertFalse(item.containsKey("_recipientsOverride"));
        Assertions.assertFalse(item.getBoolean("_subscribedByDefault"));
    }

    @Test
    void shouldNotTagSeverityWhenSeverityIsNull() {
        aggregator.aggregate(createEmailAggregation("report-upload-failed", "policy1", "host1", null));

        JsonArray reportUploadFailed = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed");

        Assertions.assertEquals(1, reportUploadFailed.size(), "the item should still be aggregated, just without a severity tag");
        Assertions.assertFalse(reportUploadFailed.getJsonObject(0).containsKey("_severity"));
    }

    @Test
    void shouldSkipEventWithNoPayload() {
        JsonObject eventWithNoPayload = new JsonObject();
        JsonObject action = new JsonObject()
            .put("event_type", "report-upload-failed")
            .put("events", new JsonArray().add(eventWithNoPayload));
        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "compliance", action, Severity.CRITICAL, UUID.randomUUID());
        aggregation.setEventTypeName("report-upload-failed");

        aggregator.aggregate(aggregation);

        JsonArray reportUploadFailed = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed");

        Assertions.assertTrue(reportUploadFailed.isEmpty(), "an event with no payload must be skipped, not crash the fold");
    }

    private static EmailAggregation createEmailAggregation(String eventType, String policyId, String inventoryId, Severity severity) {
        JsonObject payload = new JsonObject()
            .put("policy_id", policyId)
            .put("inventory_id", inventoryId);
        JsonObject event = new JsonObject().put("payload", payload);
        JsonObject action = new JsonObject()
            .put("event_type", eventType)
            .put("events", new JsonArray().add(event));

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "compliance", action, severity, UUID.randomUUID());
        aggregation.setEventTypeName(eventType);
        return aggregation;
    }
}
