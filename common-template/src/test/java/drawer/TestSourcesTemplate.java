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
class TestSourcesTemplate {


    static final String AVAILABILITY_STATUS = "availability-status";
    private static final Action ACTION = TestHelpers.createSourcesAction();

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateAvailabilityStatus() {
        String result = renderTemplate(AVAILABILITY_STATUS, ACTION);
        assertEquals("test name 1's availability status was changed from **old status** to **current status**.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "console", "sources", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
