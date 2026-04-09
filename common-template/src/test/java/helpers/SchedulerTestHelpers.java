package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class SchedulerTestHelpers {

    public static Action createSchedulerExportCompleteAction() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2026, 4, 9, 10, 30, 0, 0));
        action.setEventType("export-complete");
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("job_id", "job-12345")
                .withAdditionalProperty("job_name", "Test Export Job")
                .withAdditionalProperty("export_id", "export-67890")
                .build()
        );

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }

    public static Action createSchedulerJobFailedAction() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2026, 4, 9, 10, 30, 0, 0));
        action.setEventType("job-failed");
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("job_id", "job-54321")
                .withAdditionalProperty("job_name", "Test Failed Job")
                .withAdditionalProperty("error_message", "Connection timeout")
                .build()
        );

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }

    public static Action createSchedulerJobFailedPausedAction() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2026, 4, 9, 10, 30, 0, 0));
        action.setEventType("job-failed-paused");
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("job_id", "job-99999")
                .withAdditionalProperty("job_name", "Test Paused Job")
                .withAdditionalProperty("error_message", "Database connection failed")
                .build()
        );

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }
}
