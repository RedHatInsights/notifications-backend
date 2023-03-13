package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Inventory inventory;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setInventoryEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = inventory.getTitle(null, EmailSubscriptionType.INSTANT)
                .data("action", action)
                .data("environment", environment)
                .render();
        writeEmailTemplate(result, inventory.getTitle(null, EmailSubscriptionType.INSTANT).getTemplate().getId());

        assertTrue(result.contains(LocalDateTime.now().getYear() + " "));
        assertTrue(result.contains("- Host Validation Error triggered on Inventory"));

        // test template V2
        featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
        result = inventory.getTitle(null, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        writeEmailTemplate(result, inventory.getTitle(null, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertEquals("Instant notification - Validation error - Inventory - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyInstantEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = inventory.getTitle(null, EmailSubscriptionType.DAILY)
            .data("action", action)
            .data("environment", environment)
            .render();
        writeEmailTemplate(result, inventory.getTitle(null, EmailSubscriptionType.DAILY).getTemplate().getId());
        assertEquals("Insights Inventory daily summary", result);

        // test template V2
        featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
        result = inventory.getTitle(null, EmailSubscriptionType.DAILY)
            .data("action", action)
            .data("environment", environment)
            .render();
        writeEmailTemplate(result, inventory.getTitle(null, EmailSubscriptionType.DAILY).getTemplate().getId());
        assertEquals("Daily digest - Inventory - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = InventoryTestHelpers.createInventoryAction("", "", "", "FooEvent");
        String result = inventory.getBody(null, EmailSubscriptionType.INSTANT)
                .data("action", action)
                .data("environment", environment)
                .render();
        writeEmailTemplate(result, inventory.getBody(null, EmailSubscriptionType.INSTANT).getTemplate().getId());

        assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
        assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);

        // Event name
        assertTrue(result.contains("FooEvent"), "Body should contain the event_name");

        // test template V2
        featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
        result = inventory.getBody(null, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        writeEmailTemplate(result, inventory.getBody(null, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
        assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBody() {
        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        String result = inventory.getBody(null, EmailSubscriptionType.DAILY)
                .data("action", Map.of(
                        "context", aggregator.getContext(),
                        "timestamp", LocalDateTime.now(),
                        "bundle", "rhel"
                ))
                .data("environment", environment)
                .render();
        writeEmailTemplate(result, inventory.getBody(null, EmailSubscriptionType.DAILY).getTemplate().getId());

        JsonObject context = new JsonObject(aggregator.getContext());
        assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
        assertTrue(result.contains("Daily Inventory Summary"), "Body should contain 'Daily Inventory Summary'");
        assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
        assertTrue(result.contains("Error"), "Body should contain 'Error' header");

        // test template V2
        featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
        result = inventory.getBody(null, EmailSubscriptionType.DAILY)
            .data("action", Map.of(
                "context", aggregator.getContext(),
                "timestamp", LocalDateTime.now(),
                "bundle", "rhel"
            ))
            .data("environment", environment)
            .render();
        writeEmailTemplate(result, inventory.getBody(null, EmailSubscriptionType.DAILY).getTemplate().getId());

        context = new JsonObject(aggregator.getContext());
        assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
        assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
        assertTrue(result.contains("Error"), "Body should contain 'Error' header");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }


    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
