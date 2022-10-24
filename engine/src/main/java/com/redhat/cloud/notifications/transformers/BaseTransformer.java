package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BaseTransformer {

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
     * Transforms the given action into a {@link JsonObject}.
     * @param action the {@link Action} to transform.
     * @return a {@link JsonObject} containing the given action.
     */
    public JsonObject toJsonObject(final Action action) {
        JsonObject message = new JsonObject();

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
    }

}
