package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;

public interface CloudEventTransformer {

    Action toAction(EventWrapperCloudEvent cloudEvent, String bundle, String application, String eventType);
}
