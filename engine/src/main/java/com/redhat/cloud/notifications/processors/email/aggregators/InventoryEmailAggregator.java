package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;

public class InventoryEmailAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";
    private static final String VALIDATION_ERROR = "validation-error";
    private static final String ERRORS = "errors";

    private static final List<String> EVENT_TYPES = Arrays.asList(VALIDATION_ERROR);

    private static final String INVENTORY_KEY = "inventory";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";
    private static final String ERROR_KEY = "error";
    private static final String MESSAGE_KEY = "message";
    private static final String REQUEST_ID_KEY = "request_id";

    public InventoryEmailAggregator() {
        JsonObject inventory = new JsonObject();

        inventory.put(ERRORS, new JsonObject());

        context.put(INVENTORY_KEY, inventory);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject inventory = context.getJsonObject(INVENTORY_KEY);
        JsonObject notificationJson = notification.getPayload();
        String eventType = notificationJson.getString(EVENT_TYPE);

        if (!EVENT_TYPES.contains(eventType)) {
            return;
        }

        notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
            JsonObject error = payload.getJsonObject(ERROR_KEY);
            String errorMessage = error.getString(MESSAGE_KEY);
            String requestId = payload.getString(REQUEST_ID_KEY);

            inventory.getJsonObject(ERRORS).put(errorMessage, requestId);
        });
    }
}
