package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;

public abstract class CloudEventTransformer {

    public Action toAction(EventWrapperCloudEvent cloudEvent, String bundle, String application, String eventType) {
        Action.ActionBuilderBase<Action> builder = new Action.ActionBuilder()
                .withId(cloudEvent.getId())
                .withOrgId(cloudEvent.getOrgId())
                .withAccountId(cloudEvent.getAccountId())
                .withTimestamp(cloudEvent.getEvent().getTime())
                .withBundle(bundle)
                .withApplication(application)
                .withEventType(eventType);

        return buildAction(builder, cloudEvent)
                .build();
    }

    public abstract Action.ActionBuilderBase<?> buildAction(Action.ActionBuilderBase<Action> actionBuilder, EventWrapperCloudEvent cloudEvent);
}
