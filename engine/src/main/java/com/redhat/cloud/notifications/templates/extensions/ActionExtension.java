package com.redhat.cloud.notifications.templates.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import io.quarkus.qute.TemplateExtension;

public class ActionExtension {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // TODO NOTIF-484 Remove this annotation, it has no effect while using a standalone Qute engine.
    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromContext(Context context, String key) {
        return context.getAdditionalProperties().get(key);
    }

    // TODO NOTIF-484 Remove this annotation, it has no effect while using a standalone Qute engine.
    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromPayload(Payload payload, String key) {
        return payload.getAdditionalProperties().get(key);
    }

    // TODO NOTIF-484 Remove this annotation, it has no effect while using a standalone Qute engine.
    @TemplateExtension
    public static String toPrettyJson(Action action) {
        try {
            // Ensures we are encoding the action as per our configured encoder
            String encodedAction = Parser.encode(action);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(encodedAction));
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error while transforming action to json", jpe);
        }
    }
}
