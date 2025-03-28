package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestIntegrationsTemplate {

    public static final String INTEGRATION_DISABLED_EVENT_TYPE = "integration-disabled";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateIntegrationDisabled() {
        Action action = TestHelpers.createIntegrationDisabledAction("HTTP_4XX", "Unreliable integration", 401);
        String result = renderTemplate(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertEquals("Integration **Unreliable integration** was disabled because the remote endpoint responded with an HTTP status code 401.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "console", "integrations", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
