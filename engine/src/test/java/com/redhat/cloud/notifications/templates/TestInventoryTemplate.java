package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate extends EmailTemplatesInDbHelper {

    private static final String EVENT_TYPE_VALIDATION_ERROR = "validation-error";

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_VALIDATION_ERROR);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = generateEmailSubject(EVENT_TYPE_VALIDATION_ERROR, action);
        assertEquals("Instant notification - Validation error - Inventory - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyEmailTitle() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = generateAggregatedEmailSubject(action);
        assertEquals("Daily digest - Inventory - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = InventoryTestHelpers.createInventoryAction("", "", "", "FooEvent");
        String result = generateEmailBody(EVENT_TYPE_VALIDATION_ERROR, action);
        assertTrue(result.contains(InventoryTestHelpers.displayName1), "Body should contain host display name" + InventoryTestHelpers.displayName1);
        assertTrue(result.contains(InventoryTestHelpers.errorMessage1), "Body should contain error message" + InventoryTestHelpers.errorMessage1);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBody() {
        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        JsonObject context = new JsonObject(aggregator.getContext());
        String result = generateAggregatedEmailBody(aggregator.getContext());
        context = new JsonObject(aggregator.getContext());
        assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
        assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
        assertTrue(result.contains("Error"), "Body should contain 'Error' header");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
