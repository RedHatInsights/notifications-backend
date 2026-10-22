package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class InventoryEmailAggregatorTest {

    private static final String ORG_ID = "test-org-id";

    private InventoryEmailAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new InventoryEmailAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        aggregator.aggregate(createValidationError("host-1", "boom", Severity.CRITICAL));
        Assertions.assertEquals(ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        aggregator.aggregate(createValidationError("host-1", "boom", Severity.CRITICAL));
        aggregator.aggregate(createSystemEvent(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "new-system", Severity.LOW));
        aggregator.aggregate(createSystemEvent(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, UUID.randomUUID(), "stale-system", Severity.LOW));
        aggregator.aggregate(createSystemEvent(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_DELETED, UUID.randomUUID(), "deleted-system", Severity.LOW));

        JsonObject inventory = JsonObject.mapFrom(aggregator.getContext()).getJsonObject("inventory");

        Assertions.assertEquals(1, inventory.getJsonArray("errors").size());
        Assertions.assertEquals("boom", inventory.getJsonArray("errors").getJsonObject(0).getString("message"));
        Assertions.assertEquals(1, inventory.getJsonArray(InventoryEmailAggregator.NEW_SYSTEMS).size());
        Assertions.assertEquals(1, inventory.getJsonArray(InventoryEmailAggregator.STALE_SYSTEMS).size());
        Assertions.assertEquals(1, inventory.getJsonArray(InventoryEmailAggregator.DELETED_SYSTEMS).size());
    }

    @Test
    void shouldTagEachItemWithItsOwnSeverity() {
        aggregator.aggregate(createValidationError("host-1", "boom", Severity.CRITICAL));
        aggregator.aggregate(createSystemEvent(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "new-system", Severity.LOW));

        JsonObject inventory = JsonObject.mapFrom(aggregator.getContext()).getJsonObject("inventory");

        Assertions.assertEquals("CRITICAL", inventory.getJsonArray("errors").getJsonObject(0).getString("_severity"));
        Assertions.assertEquals("LOW", inventory.getJsonArray(InventoryEmailAggregator.NEW_SYSTEMS).getJsonObject(0).getString("_severity"));
    }

    @Test
    void shouldTagEachItemWithFullMetadata() {
        EmailAggregation aggregation = createSystemEvent(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "new-system", Severity.LOW);
        aggregation.setSubscribedByDefault(true);
        aggregation.setRecipientsOverride(new JsonArray().add(new JsonObject().put("only_admins", true)));

        aggregator.aggregate(aggregation);

        JsonObject item = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("inventory")
            .getJsonArray(InventoryEmailAggregator.NEW_SYSTEMS)
            .getJsonObject(0);

        Assertions.assertEquals(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, item.getString("_eventType"));
        Assertions.assertTrue(item.getBoolean("_subscribedByDefault"));
        Assertions.assertEquals(1, item.getJsonArray("_recipientsOverride").size());
    }

    private static EmailAggregation createValidationError(String displayName, String errorMessage, Severity severity) {
        JsonObject payload = new JsonObject()
            .put("display_name", displayName)
            .put("error", new JsonObject().put("message", errorMessage));
        JsonObject action = new JsonObject()
            .put("event_type", "validation-error")
            .put("events", new JsonArray().add(new JsonObject().put("payload", payload)));

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "inventory", action, severity, UUID.randomUUID());
        aggregation.setEventTypeName("validation-error");
        return aggregation;
    }

    private static EmailAggregation createSystemEvent(String eventType, UUID inventoryId, String displayName, Severity severity) {
        JsonObject action = new JsonObject()
            .put("event_type", eventType)
            .put("context", new JsonObject()
                .put("inventory_id", inventoryId.toString())
                .put("display_name", displayName));

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "inventory", action, severity, UUID.randomUUID());
        aggregation.setEventTypeName(eventType);
        return aggregation;
    }
}
