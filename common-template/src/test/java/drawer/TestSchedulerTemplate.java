package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_EXPORT_COMPLETE;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_JOB_FAILED;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_JOB_FAILED_PAUSED;
import static helpers.SchedulerTestHelpers.createSchedulerExportCompleteAction;
import static helpers.SchedulerTestHelpers.createSchedulerJobFailedAction;
import static helpers.SchedulerTestHelpers.createSchedulerJobFailedPausedAction;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestSchedulerTemplate {

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateExportComplete() {
        Action action = createSchedulerExportCompleteAction();
        String result = renderTemplate(SCHEDULER_EXPORT_COMPLETE, action);
        assertTrue(result.contains("A scheduled export"));
        assertTrue(result.contains("**[Test Export Job]"));
        assertTrue(result.contains("/api/exports/v1/exports/export-67890)**"));
        assertTrue(result.contains("has completed"));
    }

    @Test
    void testRenderedTemplateJobFailed() {
        Action action = createSchedulerJobFailedAction();
        String result = renderTemplate(SCHEDULER_JOB_FAILED, action);
        assertTrue(result.contains("A scheduled export"));
        assertTrue(result.contains("**[Test Failed Job]"));
        assertTrue(result.contains("/insights/jobs/job-54321)**"));
        assertTrue(result.contains("has failed"));
    }

    @Test
    void testRenderedTemplateJobFailedPaused() {
        Action action = createSchedulerJobFailedPausedAction();
        String result = renderTemplate(SCHEDULER_JOB_FAILED_PAUSED, action);
        assertTrue(result.contains("A scheduled export"));
        assertTrue(result.contains("**[Test Paused Job]"));
        assertTrue(result.contains("/insights/jobs/job-99999)**"));
        assertTrue(result.contains("has failed and been automatically paused"));
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "console", "scheduler", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
