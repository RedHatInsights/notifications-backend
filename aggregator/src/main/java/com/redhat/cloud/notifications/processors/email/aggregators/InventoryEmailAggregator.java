package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public class InventoryEmailAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";
    private static final String VALIDATION_ERROR = "validation-error";
    public static final String DELETED_SYSTEMS = "deleted_systems";
    public static final String NEW_SYSTEMS = "new_systems";
    public static final String STALE_SYSTEMS = "stale_systems";
    private static final String ERRORS = "errors";

    public static final String EVENT_TYPE_NEW_SYSTEM_REGISTERED = "new-system-registered";
    public static final String EVENT_TYPE_SYSTEM_BECAME_STALE = "system-became-stale";
    public static final String EVENT_TYPE_SYSTEM_DELETED = "system-deleted";

    private static final List<String> EVENT_TYPES = Arrays.asList(VALIDATION_ERROR);

    /**
     * Represents the list of new event types with a new payload object that
     * is sent by Inventory.
     */
    private static final List<String> NEW_EVENT_TYPES = List.of(
        EVENT_TYPE_NEW_SYSTEM_REGISTERED,
        EVENT_TYPE_SYSTEM_BECAME_STALE,
        EVENT_TYPE_SYSTEM_DELETED
    );

    private static final String CONTEXT_KEY = "context";
    private static final String INVENTORY_KEY = "inventory";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";
    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    public static final String DISPLAY_NAME_KEY = "display_name";

    public static final String INVENTORY_ID_KEY = "inventory_id";

    public InventoryEmailAggregator() {
        JsonObject inventory = new JsonObject();

        inventory.put(DELETED_SYSTEMS, new JsonArray());
        inventory.put(ERRORS, new JsonArray());
        inventory.put(NEW_SYSTEMS, new JsonArray());
        inventory.put(STALE_SYSTEMS, new JsonArray());

        context.put(INVENTORY_KEY, inventory);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject inventory = context.getJsonObject(INVENTORY_KEY);
        JsonObject notificationJson = notification.getPayload();
        String eventType = notificationJson.getString(EVENT_TYPE);

        if (VALIDATION_ERROR.equals(eventType)) {
            notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
                JsonObject event = (JsonObject) eventObject;
                JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
                JsonObject receivedErrorObject = payload.getJsonObject(ERROR_KEY);
                String errorMessage = receivedErrorObject.getString(MESSAGE_KEY);
                String displayName = payload.getString(DISPLAY_NAME_KEY);

                JsonObject error = new JsonObject();

                error.put("message", errorMessage);
                error.put("display_name", displayName);

                inventory.getJsonArray(ERRORS).add(error);
            });
        } else if (NEW_EVENT_TYPES.contains(eventType)) {
            final JsonArray systemsList;
            if (EVENT_TYPE_NEW_SYSTEM_REGISTERED.equals(eventType)) {
                systemsList = inventory.getJsonArray(NEW_SYSTEMS);
            } else if (EVENT_TYPE_SYSTEM_BECAME_STALE.equals(eventType)) {
                systemsList = inventory.getJsonArray(STALE_SYSTEMS);
            } else {
                systemsList = inventory.getJsonArray(DELETED_SYSTEMS);
            }

            final JsonObject context = notificationJson.getJsonObject(CONTEXT_KEY);

            final JsonObject system = new JsonObject();
            system.put(INVENTORY_ID_KEY, context.getString(INVENTORY_ID_KEY));
            system.put(DISPLAY_NAME_KEY, context.getString(DISPLAY_NAME_KEY));

            systemsList.add(system);
        }
    }
}
