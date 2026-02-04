package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestDefaultTemplate {

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedDefaultTemplate() {
        Action action = TestHelpers.createAdvisorAction("123456", "unknown");
        String result = renderTemplate("unknown", action);
        assertEquals("My Host triggered 4 events", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "unknown-app", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
