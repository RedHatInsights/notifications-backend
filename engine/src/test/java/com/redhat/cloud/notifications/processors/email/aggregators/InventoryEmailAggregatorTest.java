package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject inventory = JsonObject.mapFrom(context).getJsonObject("inventory");
        JsonArray errors = inventory.getJsonArray("errors");

        System.out.println(inventory.toString());

        JsonObject error1 = errors.getJsonObject(0);
        JsonObject error2 = errors.getJsonObject(1);

        Assertions.assertEquals(errors.size(), 2);
        Assertions.assertEquals(error1.getString(DISPLAY_NAME), InventoryTestHelpers.displayName1);
        Assertions.assertEquals(error1.getString(MESSAGE), InventoryTestHelpers.errorMessage1);
        Assertions.assertEquals(error2.getString(DISPLAY_NAME), InventoryTestHelpers.displayName2);
        Assertions.assertEquals(error2.getString(MESSAGE), InventoryTestHelpers.errorMessage2);
    }
}
