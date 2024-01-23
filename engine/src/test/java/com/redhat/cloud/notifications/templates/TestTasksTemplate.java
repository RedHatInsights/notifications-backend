package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TasksTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestTasksTemplate extends EmailTemplatesInDbHelper  {

    private DriftEmailPayloadAggregator aggregator;

    private static final String EVENT_TYPE_EXECUTED_TASK_COMPLETED = "executed-task-completed";
    private static final String EVENT_TYPE_EXECUTED_TASK_STARTED = "executed-task-started";
    private static final String EVENT_TYPE_JOB_COMPLETED = "job-completed";
    private static final String EVENT_TYPE_JOB_STARTED = "job-started";
    private static final String EVENT_TYPE_JOB_FAILED = "job-failed";

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Override
    protected String getApp() {
        return "tasks";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_EXECUTED_TASK_COMPLETED, EVENT_TYPE_JOB_COMPLETED, EVENT_TYPE_JOB_STARTED, EVENT_TYPE_EXECUTED_TASK_STARTED, EVENT_TYPE_JOB_FAILED);
    }

    @Test
    public void testExecutedTaskCompletedEmailTitle() {
        Action action = TasksTestHelpers.createTasksExecutedTaskCompletedAction(EVENT_TYPE_EXECUTED_TASK_COMPLETED);
        String result = generateEmailSubject(EVENT_TYPE_EXECUTED_TASK_COMPLETED, action);
        assertEquals("Instant notification - Executed task completed - Tasks - Red Hat Enterprise Linux", result);
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
    public void testExecutedTaskStartedEmailTitle() {
        Action action = TasksTestHelpers.createTasksExecutedTaskStartedAction(EVENT_TYPE_EXECUTED_TASK_STARTED);
        String result = generateEmailSubject(EVENT_TYPE_EXECUTED_TASK_STARTED, action);
        assertEquals("Instant notification - Executed task started - Tasks - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testExecutedTaskStartedEmailBody() {
        Action action = TasksTestHelpers.createTasksExecutedTaskStartedAction(EVENT_TYPE_EXECUTED_TASK_STARTED);
        String result = generateEmailBody(EVENT_TYPE_EXECUTED_TASK_STARTED, action);
        assertTrue(result.contains("Executed task started"));
        assertTrue(result.contains("has started."));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testJobCompletedEmailTitle() {
        Action action = TasksTestHelpers.createTasksJobCompletedAction(EVENT_TYPE_JOB_COMPLETED);
        String result = generateEmailSubject(EVENT_TYPE_JOB_COMPLETED, action);
        assertEquals("Instant notification - Job completed - Tasks - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testJobCompletedEmailBody() {
        Action action = TasksTestHelpers.createTasksJobCompletedAction(EVENT_TYPE_JOB_COMPLETED);
        String result = generateEmailBody(EVENT_TYPE_JOB_COMPLETED, action);
        assertTrue(result.contains("Job completed"));
        assertTrue(result.contains("from task"));
        assertTrue(result.contains("has been executed and ended with status"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testJobFailedEmailTitle() {
        Action action = TasksTestHelpers.createTasksJobFailedAction(EVENT_TYPE_JOB_FAILED);
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

    @Test
    public void testJobStartedEmailTitle() {
        Action action = TasksTestHelpers.createTasksJobStartedAction(EVENT_TYPE_JOB_STARTED);
        String result = generateEmailSubject(EVENT_TYPE_JOB_STARTED, action);
        assertEquals("Instant notification - Job started - Tasks - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testJobStartedEmailBody() {
        Action action = TasksTestHelpers.createTasksJobStartedAction(EVENT_TYPE_JOB_STARTED);
        String result = generateEmailBody(EVENT_TYPE_JOB_STARTED, action);
        assertTrue(result.contains("Job started"));
        assertTrue(result.contains("from task"));
        assertTrue(result.contains("has started"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

}
