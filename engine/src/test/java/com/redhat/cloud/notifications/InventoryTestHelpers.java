package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class InventoryTestHelpers {

    public static final String displayName1 = "random_name";
    public static final String errorMessage1 = "error 1";
    public static final String displayName2 = "random_name2";
    public static final String errorMessage2 = "error 2";

    private static final Map<String, String> ERROR1 = Map.of(
        "code", "VE001",
        "message", errorMessage1,
        "stack_trace", "",
        "severity", "error"
    );

    private static final Map<String, String> ERROR2 = Map.of(
        "code", "VE001",
        "message", errorMessage2,
        "stack_trace", "",
        "severity", "error"
    );

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String event_name) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("validation-error");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", event_name)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName1)
                                        .withAdditionalProperty("error", ERROR1)
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName2)
                                        .withAdditionalProperty("error", ERROR2)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }

    /**
     * Creates a minimal email aggregation, and fills any other fields either
     * by providing the given {@code inventoryId} or by prefixing that ID with
     * the name of the field.
     * @param eventType the type of the event to simulate the aggregation from.
     * @param inventoryId the inventory ID of the event.
     * @param displayName the display name of the host.
     * @return the generated email aggregation.
     */
    public static EmailAggregation createMinimalEmailAggregationV2(
        String eventType,
        UUID inventoryId,
        String displayName
    ) {
        final EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName("rhel");
        aggregation.setApplicationName("inventory");
        aggregation.setOrgId(DEFAULT_ORG_ID);

        final Action emailActionMessage = new Action();
        emailActionMessage.setBundle("rhel");
        emailActionMessage.setApplication("inventory");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        // Transform the tags to the expected format.
        record Tag(String value, String key) { }

        final Map<String, String> tags = Map.of("inventory-id", inventoryId.toString());

        final List<Tag> convertedTags = new ArrayList<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            convertedTags.add(
                new Tag(entry.getKey(), entry.getValue())
            );
        }

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", inventoryId)
                .withAdditionalProperty("display_name", displayName)
                .build()
        );

        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }

    public static Action createInventoryAction(String tenant, String bundle, String application, String event_name) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("validation-error");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", event_name)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName1)
                                        .withAdditionalProperty("error", ERROR1)
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
