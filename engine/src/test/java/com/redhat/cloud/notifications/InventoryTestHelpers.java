package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;

import java.time.Instant;
import java.time.LocalDateTime;
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

    public static Event createNewSystemRegisteredEvent(
        final UUID insightsId,
        final UUID subscriptionManagerId,
        final UUID satelliteId,
        final UUID groupId,
        final String groupName,
        final String reporter,
        final Instant systemCheckIn
    ) {
        return new Event.EventBuilder()
            .withMetadata(null)
            .withPayload(
                new Payload.PayloadBuilder()
                    .withAdditionalProperty("insights_id", insightsId)
                    .withAdditionalProperty("subscription_manager_id", subscriptionManagerId)
                    .withAdditionalProperty("satellite_id", satelliteId)
                    .withAdditionalProperty("group_id", groupId)
                    .withAdditionalProperty("group_name", groupName)
                    .withAdditionalProperty("reporter", reporter)
                    .withAdditionalProperty("system_check_in", systemCheckIn)
                    .build()
            )
            .build();
    }
}
