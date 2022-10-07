package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class InventoryEmailAggregatorTest {

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
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "Host Validation Failure"));
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject inventory = JsonObject.mapFrom(context).getJsonObject("inventory");
        JsonObject errors = inventory.getJsonObject("errors");

        System.out.println(inventory.toString());

        Assertions.assertFalse(errors.containsKey("foo"));
        Assertions.assertEquals(errors.size(), 2);
        Assertions.assertEquals(errors.getString(InventoryTestHelpers.errorMessage1), InventoryTestHelpers.displayName1);
        Assertions.assertEquals(errors.getString(InventoryTestHelpers.errorMessage2), InventoryTestHelpers.displayName2);
    }
}
