package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
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
    public static final String DISPLAY_NAME = "display_name";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENTS = "events";
    public static final String METADATA = "metadata";
    public static final String ORG_ID = "org_id";
    public static final String PAYLOAD = "payload";
    public static final String SOURCE = "source";
    public static final String TIMESTAMP = "timestamp";

    /**
     * Transforms the given event into a {@link JsonObject}.
     * @param event the {@link Event} to transform.
     * @return a {@link JsonObject} containing the given event.
     */
    public JsonObject toJsonObject(final Event event) {
        final JsonObject message = new JsonObject();

        final Action action = event.getAction();

        message.put(ACCOUNT_ID, action.getAccountId());
        message.put(APPLICATION, action.getApplication());
        message.put(BUNDLE, action.getBundle());
        message.put(CONTEXT, JsonObject.mapFrom(action.getContext()));
        message.put(EVENT_TYPE, action.getEventType());
        message.put(
            EVENTS,
            new JsonArray(
                action.getEvents().stream().map(
                    ev -> Map.of(
                        METADATA, JsonObject.mapFrom(ev.getMetadata()),
                        PAYLOAD, JsonObject.mapFrom(ev.getPayload())
                    )
                ).collect(Collectors.toList())
            )
        );
        message.put(ORG_ID, action.getOrgId());
        message.put(TIMESTAMP, action.getTimestamp().toString());

        // Include the display names of the different objects.
        // Since "insights-notifications-schemas-java:0.20".
        final JsonObject source = new JsonObject();

        final JsonObject sourceAppDisplayName = new JsonObject();
        sourceAppDisplayName.put(DISPLAY_NAME, event.getApplicationDisplayName());
        source.put(APPLICATION, sourceAppDisplayName);

        final JsonObject sourceBundleDisplayName = new JsonObject();
        sourceBundleDisplayName.put(DISPLAY_NAME, event.getBundleDisplayName());
        source.put(BUNDLE, sourceBundleDisplayName);

        final JsonObject sourceEventTypeDisplayName = new JsonObject();
        sourceEventTypeDisplayName.put(DISPLAY_NAME, event.getEventTypeDisplayName());
        source.put(EVENT_TYPE, sourceEventTypeDisplayName);

        message.put(SOURCE, source);

        return message;
    }
}
