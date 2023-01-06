package com.redhat.cloud.notifications.cloudevent.transformers;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PoliciesCloudEventTransformer implements CloudEventTransformer {

    @Override
    public Action toAction(EventWrapperCloudEvent cloudEvent, String bundle, String application, String eventType) {

        LocalDateTime timestamp = LocalDateTime.parse(
                cloudEvent.getEvent().get("time").asText(),
                DateTimeFormatter.ISO_DATE_TIME
        );

        JsonNode data = cloudEvent.getEvent().get("data");

        JsonNode system = data.get("system");

        Context context = new Context.ContextBuilder()
                .withAdditionalProperty("inventory_id", system.get("inventory_id").asText())
                .withAdditionalProperty("display_name", system.get("display_name").asText())
                .withAdditionalProperty("tags", StreamSupport.stream(
                        system.get("tags").spliterator(),
                        false)
                        .map(tag -> Map.of("key", tag.get("key").asText(), "value", tag.get("value").asText()))
                        .collect(Collectors.toList())
                )
                .withAdditionalProperty("system_check_in", data.get("system_check_in").asText())
                .build();

        List<Event> events = StreamSupport.stream(data.get("policies").spliterator(), false)
                .map(policy -> new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder()
                                .withAdditionalProperty("policy_id", policy.get("policy_id").asText())
                                .withAdditionalProperty("policy_name", policy.get("policy_name").asText())
                                .withAdditionalProperty("policy_description", policy.get("policy_description").asText())
                                .withAdditionalProperty("policy_condition", policy.get("policy_condition").asText())
                                .build())
                        .build())
                .collect(Collectors.toList());

        return new Action.ActionBuilder()
                .withId(cloudEvent.getId())
                .withOrgId(cloudEvent.getOrgId())
                .withAccountId(cloudEvent.getAccountId())
                .withTimestamp(timestamp)

                // bundle / application / event_type
                .withBundle(bundle)
                .withApplication(application)
                .withEventType(eventType)

                .withRecipients(Collections.emptyList()) // Policies does not make use of recipients
                .withContext(context)
                .withEvents(events)

                .build();
    }
}
