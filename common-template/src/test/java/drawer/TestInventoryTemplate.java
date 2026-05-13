package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.InventoryTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestInventoryTemplate {

    private static final String VALIDATION_ERROR = "validation-error";
    private static final String NEW_SYSTEM_REGISTERED = "new-system-registered";
    private static final String SYSTEM_BECAME_STALE = "system-became-stale";
    private static final String SYSTEM_DELETED = "system-deleted";

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateValidationError() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = renderTemplate(VALIDATION_ERROR, action);
        assertEquals("Data in a payload from insights-client was unable to be processed in the inventory due to corrupted data, incorrect values, or another issue. " +
                "If no hosts were created by this change, the error will not appear in the service. [Open Inventory](https://localhost/insights/inventory?from=notifications&integration=drawer)", result);
    }

    private static final UUID INVENTORY_ID = UUID.fromString("a0a0a0a0-b1b1-c2c2-d3d3-e4e4e4e4e4e4");

    @Test
    void testRenderedTemplateNewSystemRegistered() {
        Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", NEW_SYSTEM_REGISTERED, INVENTORY_ID, "my-new-host");
        String result = renderTemplate(NEW_SYSTEM_REGISTERED, action);
        assertEquals("The **[my-new-host](https://localhost/insights/inventory/" + INVENTORY_ID + "?from=notifications&integration=drawer)** system was registered in the inventory.", result);
    }

    @Test
    void testRenderedTemplateSystemBecameStale() {
        Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", SYSTEM_BECAME_STALE, INVENTORY_ID, "my-stale-host");
        String result = renderTemplate(SYSTEM_BECAME_STALE, action);
        assertEquals("The state of system **[my-stale-host](https://localhost/insights/inventory/" + INVENTORY_ID + "?from=notifications&integration=drawer)** changed to stale in the inventory.", result);
    }

    @Test
    void testRenderedTemplateSystemDeleted() {
        Action action = InventoryTestHelpers.createInventoryActionV2("rhel", "inventory", SYSTEM_DELETED, INVENTORY_ID, "my-deleted-host");
        String result = renderTemplate(SYSTEM_DELETED, action);
        assertEquals("The **my-deleted-host** system was deleted from the inventory.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "inventory", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
