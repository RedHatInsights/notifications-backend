package com.redhat.cloud.notifications.cloudevent.transformers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.event.apps.policies.v1.Policy;
import com.redhat.cloud.event.apps.policies.v1.PolicyTriggered;
import com.redhat.cloud.event.apps.policies.v1.SystemClass;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PolicyTriggeredCloudEventTransformer extends CloudEventTransformer {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Action.ActionBuilderBase<?> buildAction(Action.ActionBuilderBase<Action> actionBuilder, EventWrapperCloudEvent cloudEvent) {
        try {
            PolicyTriggered policyTriggered = objectMapper.treeToValue(
                    cloudEvent.getEvent().getData(),
                    PolicyTriggered.class
            );
            SystemClass rhelSystem = policyTriggered.getSystem();
            Policy[] policies = policyTriggered.getPolicies();

            Context context = new Context.ContextBuilder()
                    .withAdditionalProperty("inventory_id", rhelSystem.getInventoryID())
                    .withAdditionalProperty("display_name", rhelSystem.getDisplayName())
                    .withAdditionalProperty(
                            "tags",
                            Arrays.stream(rhelSystem.getTags()).map(tag -> Map.of(
                                    "key", tag.getKey(),
                                    "value", tag.getValue() == null ? "" : tag.getValue()
                            ))
                            .collect(Collectors.toList())
                    )
                    .withAdditionalProperty("system_check_in", rhelSystem.getCheckIn())
                    .build();

            List<Event> events = Arrays.stream(policies)
                    .map(policy -> new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(new Payload.PayloadBuilder()
                                    .withAdditionalProperty("policy_id", policy.getID())
                                    .withAdditionalProperty("policy_name", policy.getName())
                                    .withAdditionalProperty("policy_description", policy.getDescription())
                                    .withAdditionalProperty("policy_condition", policy.getCondition())
                                    .build())
                            .build())
                    .collect(Collectors.toList());

            return actionBuilder
                    .withRecipients(Collections.emptyList()) // Policies does not make use of recipients
                    .withContext(context)
                    .withEvents(events);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to parse PolicyTriggered data", e);
        }
    }
}
