package email;

import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_EXPORT_COMPLETE;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_JOB_FAILED;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.SCHEDULER_JOB_FAILED_PAUSED;
import static helpers.SchedulerTestHelpers.createSchedulerExportCompleteAction;
import static helpers.SchedulerTestHelpers.createSchedulerJobFailedAction;
import static helpers.SchedulerTestHelpers.createSchedulerJobFailedPausedAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSchedulerTemplate extends EmailTemplatesRendererHelper {

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "scheduler";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Console";
    }

    @Override
    protected String getAppDisplayName() {
        return "Scheduler";
    }

    @Test
    public void testExportCompleteEmailBody() {
        Action action = createSchedulerExportCompleteAction();
        String result = generateEmailBody(SCHEDULER_EXPORT_COMPLETE, action);
        assertTrue(result.contains("scheduled export has completed successfully"));
        assertTrue(result.contains("Test Export Job"));
        assertTrue(result.contains("/insights/jobs/"));
        assertTrue(result.contains("Download exported data"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testExportCompleteEmailTitle() {
        eventTypeDisplayName = "Scheduler export completed";
        String result = generateEmailSubject(SCHEDULER_EXPORT_COMPLETE, createSchedulerExportCompleteAction());
        assertEquals("Instant notification - Scheduler export completed - Scheduler - Console", result);
    }

    @Test
    public void testJobFailedEmailBody() {
        Action action = createSchedulerJobFailedAction();
        String result = generateEmailBody(SCHEDULER_JOB_FAILED, action);
        assertTrue(result.contains("scheduled job has failed"));
        assertTrue(result.contains("Test Failed Job"));
        assertTrue(result.contains("/insights/jobs/"));
        assertTrue(result.contains("<strong>Error:</strong>"));
        assertTrue(result.contains("Connection timeout"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testJobFailedEmailTitle() {
        eventTypeDisplayName = "Scheduler job failed";
        String result = generateEmailSubject(SCHEDULER_JOB_FAILED, createSchedulerJobFailedAction());
        assertEquals("Instant notification - Scheduler job failed - Scheduler - Console", result);
    }

    @Test
    public void testJobFailedPausedEmailBody() {
        Action action = createSchedulerJobFailedPausedAction();
        String result = generateEmailBody(SCHEDULER_JOB_FAILED_PAUSED, action);
        assertTrue(result.contains("scheduled job has failed and been automatically paused"));
        assertTrue(result.contains("Test Paused Job"));
        assertTrue(result.contains("/insights/jobs/"));
        assertTrue(result.contains("<strong>Error:</strong>"));
        assertTrue(result.contains("Database connection failed"));
        assertTrue(result.contains("The scheduled job has been paused to prevent further failures"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testJobFailedPausedEmailTitle() {
        eventTypeDisplayName = "Scheduler job failed";
        String result = generateEmailSubject(SCHEDULER_JOB_FAILED_PAUSED, createSchedulerJobFailedPausedAction());
        assertEquals("Instant notification - Scheduler job failed - Scheduler - Console", result);
    }

    @Test
    public void testJobFailedWithoutErrorMessage() {
        Action action = createSchedulerJobFailedAction();
        action.getContext().setAdditionalProperty("error_message", null);
        String result = generateEmailBody(SCHEDULER_JOB_FAILED, action);
        assertTrue(result.contains("scheduled job has failed"));
        assertTrue(result.contains("Test Failed Job"));
        // Should not contain error section if no error message
        assertTrue(!result.contains("<strong>Error:</strong>") || result.contains("<strong>Error:</strong>  "));
    }
}
