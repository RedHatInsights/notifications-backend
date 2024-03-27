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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class TestInventoryTemplate extends EmailTemplatesInDbHelper {
    @Inject
    Environment environment;

    private static final String EVENT_TYPE_NEW_SYSTEM_REGISTERED = "new-system-registered";
    private static final String EVENT_TYPE_SYSTEM_BECAME_STALE = "system-became-stale";
    private static final String EVENT_TYPE_SYSTEM_DELETED = "system-deleted";
    private static final String EVENT_TYPE_VALIDATION_ERROR = "validation-error";

    private static final String EMAIL_SUBJECT_NEW_SYSTEM_REGISTERED = "Instant notification - New system registered - Inventory - Red Hat Enterprise Linux";
    private static final String EMAIL_SUBJECT_SYSTEM_BECAME_STALE = "Instant notification - System became stale - Inventory - Red Hat Enterprise Linux";
    private static final String EMAIL_SUBJECT_SYSTEM_DELETED = "Instant notification - System deleted - Inventory - Red Hat Enterprise Linux";

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(
            EVENT_TYPE_SYSTEM_BECAME_STALE,
            EVENT_TYPE_SYSTEM_DELETED,
            EVENT_TYPE_NEW_SYSTEM_REGISTERED,
            EVENT_TYPE_VALIDATION_ERROR
        );
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

        // Add error events.
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        // Add two new system events.
        final Map<UUID, String> newSystemsMap = Map.of(
            UUID.randomUUID(), "new-system-display-name",
            UUID.randomUUID(), "new-second-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : newSystemsMap.entrySet()) {
            aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, entry.getKey(), entry.getValue()));
        }

        // Add two "system became stale" events.
        final Map<UUID, String> staleSystemsMap = Map.of(
            UUID.randomUUID(), "stale-system-display-name",
            UUID.randomUUID(), "second-stale-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : staleSystemsMap.entrySet()) {
            aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, entry.getKey(), entry.getValue()));
        }

        // Add two "system deleted" events.
        final Map<UUID, String> deletedSystemsMap = Map.of(
            UUID.randomUUID(), "deleted-system-display-name",
            UUID.randomUUID(), "second-deleted-system-display-name"
        );

        for (final Map.Entry<UUID, String> entry : deletedSystemsMap.entrySet()) {
            aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_DELETED, entry.getKey(), entry.getValue()));
        }

        JsonObject context = new JsonObject(aggregator.getContext());
        String result = generateAggregatedEmailBody(aggregator.getContext());
        context = new JsonObject(aggregator.getContext());
        assertTrue(context.getJsonObject("inventory").getJsonArray("errors").size() < 10);
        assertTrue(result.contains("Host Name"), "Body should contain 'Host Name' header");
        assertTrue(result.contains("Error"), "Body should contain 'Error' header");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));

        // Make sure that the section headline is present.
        assertTrue(result.contains("Systems"), "the \"Systems\" header was not found as the section title");
        assertTrue(result.contains(String.format("%s systems changed of state", newSystemsMap.size() + staleSystemsMap.size() + deletedSystemsMap.size())), "the header's subtitle should contain the number of systems that changed of state, but it was not found in the resulting HTML file");

        // Check that the new systems are present in the email.
        assertTrue(result.contains("New system registered"), "the email should contain the table's header for the new systems");
        for (final Map.Entry<UUID, String> entry : newSystemsMap.entrySet()) {
            this.assertSystemIsPresent(result, entry, true);
        }

        // Check that the stale systems are present in the email.
        assertTrue(result.contains("Stale system"), "the email should contain the table's header for the stale systems");
        for (final Map.Entry<UUID, String> entry : staleSystemsMap.entrySet()) {
            this.assertSystemIsPresent(result, entry, true);
        }

        // Check that the stale systems are present in the email.
        assertTrue(result.contains("System deleted"), "the email should contain the table's header for the deleted systems");
        for (final Map.Entry<UUID, String> entry : deletedSystemsMap.entrySet()) {
            this.assertSystemIsPresent(result, entry, false);
        }
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
     * Tests that the subject template for the "system became stale" event is
     * correctly rendered and contains the expected text.
     */
    @Test
    void testInstantSystemBecameStaleSubject() {
        final String hostDisplayName = "stale-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", EVENT_TYPE_SYSTEM_BECAME_STALE, inventoryId, hostDisplayName);
        final String result = this.generateEmailSubject(EVENT_TYPE_SYSTEM_BECAME_STALE, action);

        Assertions.assertEquals(EMAIL_SUBJECT_SYSTEM_BECAME_STALE, result);
    }

    /**
     * Tests that the body template for the "system became stale" event is
     * correctly rendered and contains the expected text.
     */
    @Test
    void testInstantSystemBecameStaleBody() {
        final String hostDisplayName = "stale-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", EVENT_TYPE_SYSTEM_BECAME_STALE, inventoryId, hostDisplayName);
        final String result = this.generateEmailBody(EVENT_TYPE_SYSTEM_BECAME_STALE, action);

        Assertions.assertTrue(result.contains(hostDisplayName), "the message body should contain the host's display name");

        Assertions.assertTrue(
            Pattern
                .compile(String.format("The state of system.+%s.+changed to stale in Inventory", hostDisplayName))
                .matcher(result)
                .find(),
            "the message body should indicate that the system was registered"
        );

        this.assertOpenInventoryInsightsButtonPresent(result);
    }

    /**
     * Tests that the subject template for the "system deleted" event is
     * correctly rendered and contains the expected text.
     */
    @Test
    void testInstantSystemDeletedSubject() {
        final String hostDisplayName = "deleted-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", "new-system-registered", inventoryId, hostDisplayName);
        final String result = this.generateEmailSubject(EVENT_TYPE_SYSTEM_DELETED, action);

        Assertions.assertEquals(EMAIL_SUBJECT_SYSTEM_DELETED, result);
    }

    /**
     * Tests that the body template for the "system deleted" event is correctly
     * rendered and contains the expected text.
     */
    @Test
    void testInstantSystemDeletedBody() {
        final String hostDisplayName = "deleted-host";
        final UUID inventoryId = UUID.randomUUID();

        final Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", "new-system-registered", inventoryId, hostDisplayName);
        final String result = this.generateEmailBody(EVENT_TYPE_SYSTEM_DELETED, action);

        Assertions.assertTrue(result.contains(hostDisplayName), "the message body should contain the host's display name");
        Assertions.assertTrue(result.contains("was deleted from Inventory."), "the message body should indicate that the system was deleted");

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

    /**
     * Asserts that the given system is present in the resulting HTML string.
     * @param htmlString the resulting HTML for the email.
     * @param entry the entry of the aggregated system.
     * @param isItALink if the flag is given, it will check that the system is
     *                  surrounded with a link to the system in Inventory.
     */
    private void assertSystemIsPresent(final String htmlString, final Map.Entry<UUID, String> entry, final boolean isItALink) {
        if (isItALink) {
            assertTrue(
                htmlString.contains(
                    String.format(
                        "<a target=\"_blank\" href=\"%s/insights/inventory/%s\">%s</a>",
                        this.environment.url(),
                        entry.getKey(),
                        entry.getValue()
                    )
                ),
                String.format("the resulting HTML should contain the \"%s\" system with a link to it, but it was not found", entry.getValue())
            );
        } else {
            assertTrue(
                htmlString.contains(
                    entry.getValue()), String.format("the resulting HTML should contain the \"%s\" system, but it was not found", entry.getValue()
                )
            );
        }
    }
}
