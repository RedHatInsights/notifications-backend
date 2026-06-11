package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static email.TestRoadmapTemplate.createRoadmapAction;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestRoadmapTemplate {

    static final String ROADMAP_REPORT = "roadmap-monthly-report";

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateWithData() {
        Action action = createRoadmapAction();
        String result = renderTemplate(ROADMAP_REPORT, action);
        assertEquals("Your [roadmap monthly report](https://localhost/insights/planning/roadmap?from=notifications&integration=drawer) is available: **5** deprecations, **3** changes, and **4** additions.", result);
    }

    @Test
    void testRenderedTemplateWithZeroCounts() {
        Action action = createRoadmapAction();
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecation", Map.of("count", 0))
                        .withAdditionalProperty("change", Map.of("count", 0))
                        .withAdditionalProperty("addition", Map.of("count", 0))
                        .build()
                )
                .build()
        ));
        String result = renderTemplate(ROADMAP_REPORT, action);
        assertEquals("Your [roadmap monthly report](https://localhost/insights/planning/roadmap?from=notifications&integration=drawer) is available. No roadmap changes affected your systems this month.", result);
    }

    @Test
    void testRenderedTemplateWithSingularCounts() {
        Action action = createRoadmapAction();
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecation", Map.of("count", 1))
                        .withAdditionalProperty("change", Map.of("count", 1))
                        .withAdditionalProperty("addition", Map.of("count", 1))
                        .build()
                )
                .build()
        ));
        String result = renderTemplate(ROADMAP_REPORT, action);
        assertEquals("Your [roadmap monthly report](https://localhost/insights/planning/roadmap?from=notifications&integration=drawer) is available: **1** deprecation, **1** change, and **1** addition.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "roadmap", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
