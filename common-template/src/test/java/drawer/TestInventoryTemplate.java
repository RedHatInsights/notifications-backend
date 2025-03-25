package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.InventoryTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestInventoryTemplate {

    private static final String EVENT_TYPE_NAME = "validation-error";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateValidationError() {
        Action action = InventoryTestHelpers.createInventoryAction("123456", "rhel", "inventory", "Host Validation Error");
        String result = renderTemplate(EVENT_TYPE_NAME, action);
        assertEquals("If no hosts were created by this change, the error will not appear in the service.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "inventory", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
