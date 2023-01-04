package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.notifications.events.EventDataCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;

public interface CloudEventTransformer {

    Action toAction(EventDataCloudEvent cloudEvent, String bundle, String application, String eventType);
}
