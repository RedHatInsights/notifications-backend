package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TasksTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestTasksTemplate extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_EXECUTED_TASK_COMPLETED = "executed-task-completed";

    private static final String EVENT_TYPE_JOB_FAILED = "job-failed";

    @Override
    protected String getApp() {
        return "tasks";
    }

    @Override
    protected String getAppDisplayName() {
        return "Tasks";
    }

    @Test
    public void testExecutedTaskCompletedEmailTitle() {
        Action action = TasksTestHelpers.createTasksExecutedTaskCompletedAction(EVENT_TYPE_EXECUTED_TASK_COMPLETED);
        eventTypeDisplayName = "Executed Task completed";
        String result = generateEmailSubject(EVENT_TYPE_EXECUTED_TASK_COMPLETED, action);
        assertEquals("Instant notification - Executed Task completed - Tasks - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testExecutedTaskCompletedEmailBody() {
        Action action = TasksTestHelpers.createTasksExecutedTaskCompletedAction(EVENT_TYPE_EXECUTED_TASK_COMPLETED);
        String result = generateEmailBody(EVENT_TYPE_EXECUTED_TASK_COMPLETED, action);
        assertTrue(result.contains("Executed task completed"));
        assertTrue(result.contains("has been executed and ended with status"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testJobFailedEmailTitle() {
        Action action = TasksTestHelpers.createTasksJobFailedAction(EVENT_TYPE_JOB_FAILED);
        eventTypeDisplayName = "Job failed";
        String result = generateEmailSubject(EVENT_TYPE_JOB_FAILED, action);
        assertEquals("Instant notification - Job failed - Tasks - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testJobFailedEmailBody() {
        Action action = TasksTestHelpers.createTasksJobFailedAction(EVENT_TYPE_JOB_FAILED);
        String result = generateEmailBody(EVENT_TYPE_JOB_FAILED, action);
        assertTrue(result.contains("Job failed"));
        assertTrue(result.contains("from task"));
        assertTrue(result.contains("has failed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

}
