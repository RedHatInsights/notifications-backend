package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return createEmailAggregationV2(
            "rhel",
            "inventory",
            eventType,
            inventoryId,
            String.format("hostname-%s", inventoryId),
            displayName,
            String.format("rhel-version-%s", inventoryId),
            "https://redhat.com",
            Map.of("inventory-id", inventoryId.toString()),
            inventoryId,
            inventoryId,
            inventoryId,
            inventoryId,
            String.format("group-name-%s", inventoryId),
            String.format("reporter-%s", inventoryId)
        );
    }

    /**
     * Creates an email aggregation for the new event types and the new payload
     * structure that Inventory is going to send.
     * @param bundle the bundle of the event.
     * @param application the application of the event.
     * @param eventType the type of the event.
     * @param inventoryId the ID of the inventory that originated the event.
     * @param hostname the affected host's hostname.
     * @param displayName the affected host's display name.
     * @param rhelVersion the affected host's RHEL version.
     * @param hostURL the affected host's URL.
     * @param tags any custom tags specified by Inventory.
     * @param insightsId the associated insights ID of the event.
     * @param subscriptionManagerId the associated subscription manager ID of the event.
     * @param satelliteId the associated satellite's ID.
     * @param groupId the associated group's ID.
     * @param groupName the associated group's name.
     * @param reporter the reporter of the event.
     * @return the generated email aggregation.
     */
    public static EmailAggregation createEmailAggregationV2(
        String bundle,
        String application,
        String eventType,
        UUID inventoryId,
        String hostname,
        String displayName,
        String rhelVersion,
        String hostURL,
        Map<String, String> tags,
        UUID insightsId,
        UUID subscriptionManagerId,
        UUID satelliteId,
        UUID groupId,
        String groupName,
        String reporter
    ) {
        final EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        final Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        // Transform the tags to the expected format.
        record Tag(String value, String key) { }

        final List<Tag> convertedTags = new ArrayList<>();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            convertedTags.add(
                new Tag(entry.getKey(), entry.getValue())
            );
        }

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", inventoryId)
                .withAdditionalProperty("hostname", hostname)
                .withAdditionalProperty("display_name", displayName)
                .withAdditionalProperty("rhel_version", rhelVersion)
                .withAdditionalProperty("host_url", hostURL)
                .withAdditionalProperty("tags", convertedTags)
                .build()
        );

        emailActionMessage.setEvents(
            List.of(
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("insights_id", insightsId)
                            .withAdditionalProperty("subscription_manager_id", subscriptionManagerId)
                            .withAdditionalProperty("satellite_id", satelliteId)
                            .withAdditionalProperty("group_id", groupId)
                            .withAdditionalProperty("group_name", groupName)
                            .withAdditionalProperty("reporter", reporter)
                            .withAdditionalProperty("system_check_in", Instant.now(Clock.systemUTC()))
                            .build()
                    )
                    .build()
            )
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

    /**
     * Creates a minimal aggregation with the {@link #createMinimalEmailAggregationV2(String, UUID, String)}
     * function, adds it to the given aggregator, and returns the generated
     * elements in case they need to be used for later assertions.
     * @param aggregator the aggregator to add the aggregations to.
     * @param eventType the event type of the aggregation to generate.
     * @param displayNames the display names of the systems to aggregate.
     * @return the generated systems' values.
     */
    public static Map<UUID, String> addMinimalAggregation(final InventoryEmailAggregator aggregator, final String eventType, final Set<String> displayNames) {
        final Map<UUID, String> systemsMap = new HashMap<>();

        for (final String displayName : displayNames) {
            final UUID systemId = UUID.randomUUID();

            systemsMap.put(systemId, displayName);
            aggregator.aggregate(
                InventoryTestHelpers.createMinimalEmailAggregationV2(
                    eventType,
                    systemId,
                    displayName
                )
            );
        }

        return systemsMap;
    }
}
