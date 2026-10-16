package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

public class TestHelpers {

    public static JsonObject wrapActionToJsonObject(final Action action) {
        return wrapActionToJsonObject(action, null);
    }

    public static JsonObject wrapActionToJsonObject(final Action action, final String eventTypeDisplayName) {
        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event();
        event.setEventWrapper(new EventWrapperAction(action));
        if (eventTypeDisplayName != null) {
            event.setEventTypeDisplayName(eventTypeDisplayName);
        }
        return new BaseTransformer().toJsonObject(event);
    }

    public static EmailAggregation createEmailAggregationFromAction(Action emailActionMessage) {
        return createEmailAggregationFromAction(emailActionMessage, null);
    }

    public static EmailAggregation createEmailAggregationFromAction(Action emailActionMessage, String eventTypeDisplayName) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(emailActionMessage.getBundle());
        aggregation.setApplicationName(emailActionMessage.getApplication());
        aggregation.setOrgId(emailActionMessage.getOrgId());
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage, eventTypeDisplayName));
        return aggregation;
    }

}
