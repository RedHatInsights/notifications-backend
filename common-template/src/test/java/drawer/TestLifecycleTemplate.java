package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static email.TestLifecycleTemplate.createLifecycleAction;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestLifecycleTemplate {

    static final String RETIRING_LIFECYCLE_REPORT = "retiring-lifecycle-monthly-report";

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateWithData() {
        Action action = createLifecycleAction();
        String result = renderTemplate(RETIRING_LIFECYCLE_REPORT, action);
        assertEquals("Your [life cycle monthly report](https://localhost/insights/planning/lifecycle?from=notifications&integration=drawer) is available: **11** retired and **29** expiring releases.", result);
    }

    @Test
    void testRenderedTemplateWithZeroCounts() {
        Action action = createLifecycleAction();
        action.setEvents(java.util.List.of(
            new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                .withMetadata(new com.redhat.cloud.notifications.ingress.Metadata.MetadataBuilder().build())
                .withPayload(
                    new com.redhat.cloud.notifications.ingress.Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", java.util.Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .withAdditionalProperty("rhel_near_retirement", java.util.Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .build()
                )
                .build()
        ));
        String result = renderTemplate(RETIRING_LIFECYCLE_REPORT, action);
        assertEquals("Your [life cycle monthly report](https://localhost/insights/planning/lifecycle?from=notifications&integration=drawer) is available. Your inventory remained fully supported.", result);
    }

    @Test
    void testRenderedTemplateWithSingleRelease() {
        Action action = createLifecycleAction();
        action.setEvents(java.util.List.of(
            new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                .withMetadata(new com.redhat.cloud.notifications.ingress.Metadata.MetadataBuilder().build())
                .withPayload(
                    new com.redhat.cloud.notifications.ingress.Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", java.util.Map.of("rhel_versions_count", 1, "systems_count", 5))
                        .withAdditionalProperty("rhel_near_retirement", java.util.Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .build()
                )
                .build()
        ));
        String result = renderTemplate(RETIRING_LIFECYCLE_REPORT, action);
        assertEquals("Your [life cycle monthly report](https://localhost/insights/planning/lifecycle?from=notifications&integration=drawer) is available: **1** retired and **0** expiring releases.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "life-cycle", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
