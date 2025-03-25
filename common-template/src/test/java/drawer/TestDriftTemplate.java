package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.DriftTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestDriftTemplate {

    static final String EVENT_TYPE_NAME = "drift-baseline-detected";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateDrift() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = renderTemplate(EVENT_TYPE_NAME, action);
        assertEquals("**Machine 1** has drifted from 2 baselines.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "drift", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
