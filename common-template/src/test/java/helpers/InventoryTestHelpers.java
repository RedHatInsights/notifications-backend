package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

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

    /**
     * Creates an inventory action.
     * @param bundle the bundle of the triggered event.
     * @param application the application of the triggered event.
     * @param eventType the type of the triggered event.
     * @param inventoryId the inventory object that triggered the event.
     * @param hostDisplayName the display name of the host that triggered the
     *                        event.
     * @return the build action.
     */
    public static Action createInventoryActionV2(
        final String bundle,
        final String application,
        final String eventType,
        final UUID inventoryId,
        final String hostDisplayName
    ) {
        final Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("display_name", hostDisplayName)
                .withAdditionalProperty("inventory_id", inventoryId)
                .build()
        );

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
