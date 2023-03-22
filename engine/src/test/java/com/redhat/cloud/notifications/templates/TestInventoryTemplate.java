package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate extends EmailTemplatesInDbHelper {

    private static final String EVENT_TYPE_NAME = "validation-error";

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setInventoryEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(EVENT_TYPE_NAME, action);
            assertTrue(result.contains(LocalDateTime.now().getYear() + " "));
            assertTrue(result.contains("- Host Validation Error triggered on Inventory"));

            featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(EVENT_TYPE_NAME, action);
            assertEquals("Instant notification - Validation error - Inventory - Red Hat Enterprise Linux", result);
        });
    }

    @Test
    public void testDailyEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailSubject(action);
            assertEquals("Insights Inventory daily summary", result);

            featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailSubject(action);
            assertEquals("Daily digest - Inventory - Red Hat Enterprise Linux", result);
        });
    }

    @Test
    public void testInstantEmailBody() {
        Action action = InventoryTestHelpers.createInventoryAction("", "", "", "FooEvent");
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(EVENT_TYPE_NAME, action);
            assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
            assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);
            assertTrue(result.contains("FooEvent"), "Body should contain the event_name");

            featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(EVENT_TYPE_NAME, action);
            assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
            assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testDailyEmailBody() {
        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailBody(aggregator.getContext());
            JsonObject context = new JsonObject(aggregator.getContext());
            assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
            assertTrue(result.contains("Daily Inventory Summary"), "Body should contain 'Daily Inventory Summary'");
            assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
            assertTrue(result.contains("Error"), "Body should contain 'Error' header");

            featureFlipper.setInventoryEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailBody(aggregator.getContext());
            context = new JsonObject(aggregator.getContext());
            assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
            assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
            assertTrue(result.contains("Error"), "Body should contain 'Error' header");
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }
}
