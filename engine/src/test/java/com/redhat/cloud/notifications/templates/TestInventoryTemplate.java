package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

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

        assertTrue(result.contains(InventoryTestHelpers.requestId1), "Body should contain host display name" + InventoryTestHelpers.requestId1);
        assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);

        // Event name
        assertTrue(result.contains("FooEvent"), "Body should contain the event_name");
    }
}
