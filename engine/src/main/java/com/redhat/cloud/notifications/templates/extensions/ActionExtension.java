package com.redhat.cloud.notifications.templates.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import io.quarkus.qute.TemplateExtension;

public class ActionExtension {


    private static final ObjectMapper objectMapper = new ObjectMapper()
                                                            .registerModule(new Jdk8Module())
                                                            .registerModule(new JavaTimeModule());

    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromContext(Context context, String key) {
        return context.getAdditionalProperties().get(key);
    }

    @TemplateExtension
    public static boolean isCloudEvent(Action action) {
        return false;
    }

    @TemplateExtension
    public static boolean isCloudEvent(NotificationsConsoleCloudEvent event) {
        return true;
    }

    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromPayload(Payload payload, String key) {
        return payload.getAdditionalProperties().get(key);
    }

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

    @TemplateExtension
    public static String toPrettyJson(NotificationsConsoleCloudEvent event) {
        try {
            String encodedAction = toJson(event);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(encodedAction));
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error while transforming action to json", jpe);
        }
    }

    public static String toJson(NotificationsConsoleCloudEvent consoleCloudEvent) {
        try {
            JsonNode node = objectMapper.valueToTree(consoleCloudEvent);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException var3) {
            throw new RuntimeException("Cloud event serialization failed consoleCloudEvent: " + consoleCloudEvent, var3);
        }
    }
}
