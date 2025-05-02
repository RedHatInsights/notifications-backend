package com.redhat.cloud.notifications.qute.templates.extensions;

import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Payload;
import io.quarkus.qute.TemplateExtension;

public class ActionExtension {

    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromContext(Context context, String key) {
        return context.getAdditionalProperties().get(key);
    }

    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromPayload(Payload payload, String key) {
        return payload.getAdditionalProperties().get(key);
    }
}
