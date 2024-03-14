package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate extends EmailTemplatesInDbHelper {
    @Inject
    Environment environment;

    private static final String EVENT_TYPE_NEW_SYSTEM_REGISTERED = "new-system-registered";
    private static final String EVENT_TYPE_VALIDATION_ERROR = "validation-error";

    private static final String EMAIL_SUBJECT_NEW_SYSTEM_REGISTERED = "Instant notification - New system registered - Inventory - Red Hat Enterprise Linux";

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NEW_SYSTEM_REGISTERED, EVENT_TYPE_VALIDATION_ERROR);
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

    /**
     * Tests that the subject template for the "new system registered" event is
     * correctly rendered and contains the expected text.
     */
    @Test
    void testInstantNewSystemRegisteredSubject() {
        final String hostDisplayName = "new-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", EVENT_TYPE_NEW_SYSTEM_REGISTERED, inventoryId, hostDisplayName);
        final String result = this.generateEmailSubject(EVENT_TYPE_NEW_SYSTEM_REGISTERED, action);

        Assertions.assertEquals(EMAIL_SUBJECT_NEW_SYSTEM_REGISTERED, result);
    }

    /**
     * Tests that the subject body for the "new system registered" event is
     * correctly rendered and contains the expected text.
     */
    @Test
    void testInstantNewSystemRegisteredBody() {
        final String hostDisplayName = "new-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", EVENT_TYPE_NEW_SYSTEM_REGISTERED, inventoryId, hostDisplayName);
        final String result = this.generateEmailBody(EVENT_TYPE_NEW_SYSTEM_REGISTERED, action);

        Assertions.assertTrue(result.contains(hostDisplayName), "the message body should contain the host's display name");
        Assertions.assertTrue(result.contains("was registered in Inventory."), "the message body should indicate that the system was registered");

        this.assertOpenInventoryInsightsButtonPresent(result);
    }

    /**
     * Asserts that the "Open Inventory in Insights" button is present.
     * @param result the resulting HTML in which we need to perform the
     *               assertion.
     */
    private void assertOpenInventoryInsightsButtonPresent(final String result) {
        Assertions.assertTrue(
            result.contains(
                String.format(
                    "<a target=\"_blank\" href=\"%s/insights/inventory/\">Open Inventory in Insights</a>",
                    this.environment.url()
                )
            )
        );
    }
}
