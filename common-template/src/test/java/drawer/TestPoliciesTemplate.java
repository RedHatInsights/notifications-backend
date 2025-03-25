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
class TestPoliciesTemplate {

    private static final String EVENT_TYPE_NAME = "policy-triggered";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateNewAdvisory() {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");
        String result = renderTemplate(EVENT_TYPE_NAME, action);
        assertEquals("**FooMachine** triggered 2 policies.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "policies", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
