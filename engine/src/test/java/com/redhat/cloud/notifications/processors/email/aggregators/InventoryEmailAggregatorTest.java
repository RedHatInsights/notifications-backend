package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class InventoryEmailAggregatorTest {

    private InventoryEmailAggregator aggregator;

    private static final String DISPLAY_NAME = "display_name";
    private static final String MESSAGE = "message";

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
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "Host Validation Failure"));
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        // Add a "validation error" event type to the aggregation.
        this.aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        // Add two new system events.
        final Map<UUID, String> newSystemsMap = Map.of(
            UUID.randomUUID(), "new-system-display-name",
            UUID.randomUUID(), "new-second-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : newSystemsMap.entrySet()) {
            this.aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, entry.getKey(), entry.getValue()));
        }

        // Add two "system became stale" events.
        final Map<UUID, String> staleSystemsMap = Map.of(
            UUID.randomUUID(), "stale-system-display-name",
            UUID.randomUUID(), "second-stale-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : staleSystemsMap.entrySet()) {
            this.aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, entry.getKey(), entry.getValue()));
        }

        // Add two "system deleted" events.
        final Map<UUID, String> deletedSystemsMap = Map.of(
            UUID.randomUUID(), "deleted-system-display-name",
            UUID.randomUUID(), "second-deleted-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : deletedSystemsMap.entrySet()) {
            this.aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_DELETED, entry.getKey(), entry.getValue()));
        }

        Map<String, Object> context = aggregator.getContext();
        JsonObject inventory = JsonObject.mapFrom(context).getJsonObject("inventory");

        // Validate aggregated data from "validation error" event type.
        JsonArray errors = inventory.getJsonArray("errors");

        JsonObject error1 = errors.getJsonObject(0);
        JsonObject error2 = errors.getJsonObject(1);

        Assertions.assertEquals(errors.size(), 2);
        Assertions.assertEquals(error1.getString(DISPLAY_NAME), InventoryTestHelpers.displayName1);
        Assertions.assertEquals(error1.getString(MESSAGE), InventoryTestHelpers.errorMessage1);
        Assertions.assertEquals(error2.getString(DISPLAY_NAME), InventoryTestHelpers.displayName2);
        Assertions.assertEquals(error2.getString(MESSAGE), InventoryTestHelpers.errorMessage2);

        // Validate the systems' array.
        final JsonArray deletedSystems = inventory.getJsonArray(InventoryEmailAggregator.DELETED_SYSTEMS);
        final JsonArray newSystems = inventory.getJsonArray(InventoryEmailAggregator.NEW_SYSTEMS);
        final JsonArray staleSystems = inventory.getJsonArray(InventoryEmailAggregator.STALE_SYSTEMS);

        this.assertInventoryContainsExpectedElements(deletedSystemsMap, deletedSystems, "deleted systems");
        this.assertInventoryContainsExpectedElements(newSystemsMap, newSystems, "new systems");
        this.assertInventoryContainsExpectedElements(staleSystemsMap, staleSystems, "stale systems");
    }

    /**
     * Asserts that the expected elements are found in the provided JSON array,
     * which should contain the aggregated results of the different systems in
     * their corresponding status.
     * @param expectedElements the expected elements to be found in the JSON
     *                         array.
     * @param resultingElements the resulting JSON array from the aggregation.
     * @param arrayType the kind of array type we are evaluating. Helpful for
     *                  having more accurate error messages.
     */
    private void assertInventoryContainsExpectedElements(final Map<UUID, String> expectedElements, final JsonArray resultingElements, final String arrayType) {
        Assertions.assertEquals(expectedElements.size(), resultingElements.size(), String.format("unexpected number of %s in the resulting JSON array", arrayType));

        for (final Object ds : resultingElements) {
            final JsonObject jsonSystem = (JsonObject) ds;
            final String jsonId = jsonSystem.getString(InventoryEmailAggregator.INVENTORY_ID_KEY);
            Assertions.assertNotNull(jsonId, String.format("unable to extract the '%s' key from the resulting %s array", InventoryEmailAggregator.INVENTORY_ID_KEY, arrayType));

            final String displayName = expectedElements.get(
                UUID.fromString(
                    jsonSystem.getString(InventoryEmailAggregator.INVENTORY_ID_KEY)
                )
            );

            Assertions.assertEquals(displayName, jsonSystem.getString(InventoryEmailAggregator.DISPLAY_NAME_KEY), String.format("unexpected associated display name '%s' for system with ID '%s'", displayName, jsonId));
        }
    }
}
