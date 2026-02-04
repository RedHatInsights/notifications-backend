package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.PatchTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestPatchTemplate {

    static final String NEW_ADVISORY = "new-advisory";

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateNewAdvisory() {
        String result = renderTemplate(NEW_ADVISORY, ACTION);
        assertEquals("Red Hat Insights has just released new Advisories for your organization. Please review the systems affected and all the details of each errata.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "patch", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
