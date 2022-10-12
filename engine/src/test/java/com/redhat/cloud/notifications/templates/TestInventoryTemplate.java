package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate {

    @Inject
    Environment environment;

    @Test
    public void testInstantEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = Inventory.Templates.validationErrorEmailTitle()
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result.contains("2022 "));
        assertTrue(result.contains("- Host Validation Error triggered on Inventory"));
    }

    @Test
    public void testInstantEmailBody() {
        Action action = InventoryTestHelpers.createInventoryAction("", "", "", "FooEvent");
        String result = Inventory.Templates.validationErrorEmailBody()
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
        assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);

        // Event name
        assertTrue(result.contains("FooEvent"), "Body should contain the event_name");
    }

    @Test
    public void testDailyEmailBody() {
        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        String result = Inventory.Templates.dailyEmailBody()
                .data("action", Map.of(
                        "context", aggregator.getContext(),
                        "timestamp", LocalDateTime.now()
                ))
                .data("environment", environment)
                .render();

        JsonObject context = new JsonObject(aggregator.getContext());
        assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
        assertTrue(result.contains("Daily Inventory Summary"), "Body should contain 'Daily Inventory Summary'");
        assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
        assertTrue(result.contains("Error"), "Body should contain 'Error' header");
    }
}
