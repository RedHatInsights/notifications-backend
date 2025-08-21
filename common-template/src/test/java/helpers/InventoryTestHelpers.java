package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class InventoryTestHelpers {

    public static final String DISPLAY_NAME_1 = "random_name";
    public static final String ERROR_MESSAGE_1 = "error 1";

    private static final Map<String, String> ERROR_1 = Map.of(
        "code", "VE001",
        "message", ERROR_MESSAGE_1,
        "stack_trace", "",
        "severity", "error"
    );

    public static Action createInventoryAction(String tenant, String bundle, String application, String eventName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("validation-error");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", eventName)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", DISPLAY_NAME_1)
                                        .withAdditionalProperty("error", ERROR_1)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
