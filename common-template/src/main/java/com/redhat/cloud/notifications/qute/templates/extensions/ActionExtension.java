package com.redhat.cloud.notifications.qute.templates.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import io.quarkus.qute.TemplateExtension;

public class ActionExtension {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Add Jdk8Module on cloudEvent parser library since jackson dependency bumped to 2.16.1
    private static final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser(ConsoleCloudEventParser.buildObjectMapper().registerModules(new Jdk8Module()));

    @TemplateExtension(matchName = TemplateExtension.ANY)
    public static Object getFromContext(Context context, String key) {
        return context.getAdditionalProperties().get(key);
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
    public static String toPrettyJson(ConsoleCloudEvent event) {
        try {
            String encodedAction = consoleCloudEventParser.toJson(event);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(encodedAction));
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error while transforming action to json", jpe);
        }
    }
}
