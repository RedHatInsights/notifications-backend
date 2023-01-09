package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class CloudEventTransformerBase implements CloudEventTransformer {

    private final static String CE_KEY_TIME = "time";

    @Override
    public Action toAction(EventWrapperCloudEvent cloudEvent, String bundle, String application, String eventType) {
        LocalDateTime timestamp = LocalDateTime.parse(
                cloudEvent.getEvent().get(CE_KEY_TIME).asText(),
                DateTimeFormatter.ISO_DATE_TIME
        );

        Action.ActionBuilderBase<Action> builder = new Action.ActionBuilder()
                .withId(cloudEvent.getId())
                .withOrgId(cloudEvent.getOrgId())
                .withAccountId(cloudEvent.getAccountId())
                .withTimestamp(timestamp)
                .withBundle(bundle)
                .withApplication(application)
                .withEventType(eventType);

        return buildAction(builder, cloudEvent)
                .build();
    }

    public abstract Action.ActionBuilderBase<?> buildAction(Action.ActionBuilderBase<Action> actionBuilder, EventWrapperCloudEvent cloudEvent);
}
