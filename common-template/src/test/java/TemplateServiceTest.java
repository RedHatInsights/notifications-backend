
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.BUNDLE_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_INTEGRATION_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class TemplateServiceTest {

    @Inject
    TemplateService templateService;

    @Test
    void testBetaToGaFallback() {
        final Action action = TestHelpers.createIntegrationDisabledAction("HTTP_4XX", "Unreliable integration", 401);

        // Render default drawer notification
        final TemplateDefinition defaultDrawerTemplateDefinition = new TemplateDefinition(DRAWER, null, null, null);
        final String defaultTemplateResult = templateService.renderTemplate(defaultDrawerTemplateDefinition, action);

        // Render default drawer notification
        TemplateDefinition templateDefinition = new TemplateDefinition(DRAWER, BUNDLE_NAME, INTEGRATIONS_APP_NAME, INTEGRATIONS_INTEGRATION_DISABLED, true);

        // specified beta version don't exist, but its GA version exists
        String result = templateService.renderTemplate(templateDefinition, action);

        assertFalse(result.isEmpty());
        // rendered template must be different than default one
        assertNotEquals(result, defaultTemplateResult);

        // define an not existing beta template, without existing GA version
        templateDefinition = new TemplateDefinition(DRAWER, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "I don't exist", true);
        result = templateService.renderTemplate(templateDefinition, action);

        // rendered template must be the same as default one
        assertEquals(result, defaultTemplateResult);

    }
}
