package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TasksTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestTasksTemplate {

    static final String EXECUTED_TASK_COMPLETED = "executed-task-completed";
    static final String JOB_FAILED = "job-failed";

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateExecutedTaskCompleted() {
        Action action = TasksTestHelpers.createTasksExecutedTaskCompletedAction(EXECUTED_TASK_COMPLETED);
        String result = renderTemplate(EXECUTED_TASK_COMPLETED, action);
        assertEquals("The **[RHEL pre-upgrade analysis utility](https://localhost/insights/tasks/executed/9651?from=notifications&integration=drawer)** task was executed and completed with the **Completed** status.", result);
    }

    @Test
    void testRenderedTemplateJobFailed() {
        Action action = TasksTestHelpers.createTasksJobFailedAction(JOB_FAILED);
        String result = renderTemplate(JOB_FAILED, action);
        assertEquals("The **[iqe-jenkins-tasks-rhel-89-prod](https://localhost/insights/tasks/executed/10750?from=notifications&integration=drawer)** job from the **test_task** task failed with the **TIMEOUT** status.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "tasks", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
