package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    // Maximum number of systems/errors to keep per category to prevent memory issues
    public static final int MAXIMUM_SYSTEMS_PER_CATEGORY = 500;
    public static final int MAXIMUM_ERRORS = 500;

    private final JsonArray deletedSystems = new JsonArray();
    private final JsonArray errors = new JsonArray();
    private final JsonArray newSystems = new JsonArray();
    private final JsonArray staleSystems = new JsonArray();

    private boolean errorsLimitWarned = false;
    private boolean newSystemsLimitWarned = false;
    private boolean staleSystemsLimitWarned = false;
    private boolean deletedSystemsLimitWarned = false;

    public InventoryEmailAggregator() {
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        JsonObject notificationJson = notification.getPayload();
        String eventType = notificationJson.getString(EVENT_TYPE);

        if (VALIDATION_ERROR.equals(eventType)) {
            notificationJson.getJsonArray(EVENTS_KEY).stream().forEach(eventObject -> {
                if (errors.size() >= MAXIMUM_ERRORS) {
                    if (!errorsLimitWarned) {
                        Log.warnf("Validation errors limit reached (%d) for org_id=%s, additional errors will be dropped",
                                  MAXIMUM_ERRORS, getOrgId());
                        errorsLimitWarned = true;
                    }
                    return;
                }

                JsonObject event = (JsonObject) eventObject;
                JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
                JsonObject receivedErrorObject = payload.getJsonObject(ERROR_KEY);
                String errorMessage = receivedErrorObject.getString(MESSAGE_KEY);
                String displayName = payload.getString(DISPLAY_NAME_KEY);

                JsonObject error = new JsonObject();

                error.put("message", errorMessage);
                error.put("display_name", displayName);

                errors.add(error);
            });
        } else if (NEW_EVENT_TYPES.contains(eventType)) {
            final JsonArray systemsList;
            final String categoryName;
            boolean limitWarned;

            if (EVENT_TYPE_NEW_SYSTEM_REGISTERED.equals(eventType)) {
                systemsList = newSystems;
                categoryName = "new systems";
                limitWarned = newSystemsLimitWarned;
            } else if (EVENT_TYPE_SYSTEM_BECAME_STALE.equals(eventType)) {
                systemsList = staleSystems;
                categoryName = "stale systems";
                limitWarned = staleSystemsLimitWarned;
            } else {
                systemsList = deletedSystems;
                categoryName = "deleted systems";
                limitWarned = deletedSystemsLimitWarned;
            }

            // Check size limit before adding
            if (systemsList.size() < MAXIMUM_SYSTEMS_PER_CATEGORY) {
                final JsonObject notifContext = notificationJson.getJsonObject(CONTEXT_KEY);

                final JsonObject system = new JsonObject();
                system.put(INVENTORY_ID_KEY, notifContext.getString(INVENTORY_ID_KEY));
                system.put(DISPLAY_NAME_KEY, notifContext.getString(DISPLAY_NAME_KEY));

                systemsList.add(system);
            } else if (!limitWarned) {
                Log.warnf("%s limit reached (%d) for org_id=%s, additional systems will be dropped",
                          categoryName, MAXIMUM_SYSTEMS_PER_CATEGORY, getOrgId());
                if (EVENT_TYPE_NEW_SYSTEM_REGISTERED.equals(eventType)) {
                    newSystemsLimitWarned = true;
                } else if (EVENT_TYPE_SYSTEM_BECAME_STALE.equals(eventType)) {
                    staleSystemsLimitWarned = true;
                } else {
                    deletedSystemsLimitWarned = true;
                }
            }
        }
    }

    @Override
    public Map<String, Object> getContext() {
        // Populate the context with the final aggregated data
        JsonObject inventory = new JsonObject();
        inventory.put(DELETED_SYSTEMS, deletedSystems);
        inventory.put(ERRORS, errors);
        inventory.put(NEW_SYSTEMS, newSystems);
        inventory.put(STALE_SYSTEMS, staleSystems);

        context.put(INVENTORY_KEY, inventory);

        return super.getContext();
    }
}
