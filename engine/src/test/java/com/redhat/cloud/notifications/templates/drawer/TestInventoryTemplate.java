package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestInventoryTemplate extends IntegrationTemplatesInDbHelper {

    private static final String EVENT_TYPE_NAME = "validation-error";

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    void testRenderedTemplateValidationError() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = generateDrawerTemplate(EVENT_TYPE_NAME, action);
        assertEquals("If no hosts were created by this change, the error will not appear in the service.", result);
    }
}
