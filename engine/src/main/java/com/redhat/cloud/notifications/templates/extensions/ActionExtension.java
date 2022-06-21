package com.redhat.cloud.notifications.templates.extensions;

import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Payload;
import io.quarkus.qute.TemplateExtension;

// TODO NOTIF-484 Remove this annotation, it has no effect while using a standalone Qute engine.
@TemplateExtension(matchName = TemplateExtension.ANY)
public class ActionExtension {

    public static Object getFromContext(Context context, String key) {
        return context.getAdditionalProperties().get(key);
    }

    public static Object getFromPayload(Payload payload, String key) {
        return payload.getAdditionalProperties().get(key);
    }

}
