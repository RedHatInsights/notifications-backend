package com.redhat.cloud.notifications.transformers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.events.EventData;
import com.redhat.cloud.notifications.events.EventDataAction;
import com.redhat.cloud.notifications.events.EventDataCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BaseTransformer {

    @Inject
    ObjectMapper objectMapper;

    // JSON property names' definition.
    public static final String ACCOUNT_ID = "account_id";
    public static final String APPLICATION = "application";
    public static final String BUNDLE = "bundle";
    public static final String CONTEXT = "context";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENTS = "events";
    public static final String METADATA = "metadata";
    public static final String ORG_ID = "org_id";
    public static final String PAYLOAD = "payload";
    public static final String TIMESTAMP = "timestamp";

    /**
     * Transforms the given event data into a {@link JsonObject}.
     * @param eventData the {@link EventData} to transform.
     * @return a {@link JsonObject} containing the given event data.
     */
    public JsonObject toJsonObject(final EventData<?, ?> eventData) {
        if (eventData instanceof EventDataAction) {
            JsonObject message = new JsonObject();
            Action action = ((EventDataAction) eventData).getRawEvent();
            message.put(ACCOUNT_ID, action.getAccountId());
            message.put(APPLICATION, action.getApplication());
            message.put(BUNDLE, action.getBundle());
            message.put(CONTEXT, JsonObject.mapFrom(action.getContext()));
            message.put(EVENT_TYPE, action.getEventType());
            message.put(
                    EVENTS,
                    new JsonArray(
                            action.getEvents().stream().map(
                                    event -> Map.of(
                                            METADATA, JsonObject.mapFrom(event.getMetadata()),
                                            PAYLOAD, JsonObject.mapFrom(event.getPayload())
                                    )
                            ).collect(Collectors.toList())
                    )
            );
            message.put(ORG_ID, action.getOrgId());
            message.put(TIMESTAMP, action.getTimestamp().toString());
            return message;
        } else if (eventData instanceof EventDataCloudEvent) {
            JsonNode cloudEvent = ((EventDataCloudEvent) eventData).getRawEvent();
            try {
                return JsonObject.mapFrom(objectMapper.treeToValue(cloudEvent, Map.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error transforming cloud event to JsonObject for sending to integrations", e);
            }
        }

        throw new RuntimeException("Unknown eventData received");
    }

}
