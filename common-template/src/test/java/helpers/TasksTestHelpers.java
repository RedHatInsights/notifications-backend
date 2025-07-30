package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class TasksTestHelpers {

    private static Action createTasksAction(String eventType, Payload payload) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("rhel");
        emailActionMessage.setApplication("tasks");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(payload)
                .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createTasksExecutedTaskCompletedAction(String eventType) {
        Payload payload = new Payload.PayloadBuilder()
            .withAdditionalProperty("task_name", "RHEL pre-upgrade analysis utility")
            .withAdditionalProperty("executed_task_id", 9651)
            .withAdditionalProperty("status", "Completed")
            .build();

        return createTasksAction(eventType, payload);
    }

    public static Action createTasksJobFailedAction(String eventType) {
        Payload payload = new Payload.PayloadBuilder()
            .withAdditionalProperty("system_uuid", "0c1fa20b-889b-469c-993d-775c06480cd8")
            .withAdditionalProperty("display_name", "iqe-jenkins-tasks-rhel-89-prod")
            .withAdditionalProperty("status", "TIMEOUT")
            .build();
        Action action = createTasksAction(eventType, payload);

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("task_name", "test_task")
                .withAdditionalProperty("task_slug", "leapp-preupgrade")
                .withAdditionalProperty("executed_task_id", 10750)
                .build()
        );
        return action;
    }
}
