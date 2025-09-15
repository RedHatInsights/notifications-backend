package com.redhat.cloud.notifications.connector.pagerduty;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.format.DateTimeFormatter;

/**
 * Utility class for transforming PagerDuty payloads.
 * Contains constants and helper methods for PagerDuty integration.
 * This version removes Camel dependencies from the original implementation.
 */
@ApplicationScoped
public class PagerDutyTransformer {

    // CloudEvent payload field names
    public static final String ACCOUNT_ID = "account_id";
    public static final String AUTHENTICATION = "authentication";
    public static final String APPLICATION = "application";
    public static final String APPLICATION_URL = "application_url";
    public static final String BUNDLE = "bundle";
    public static final String CONTEXT = "context";
    public static final String CUSTOM_DETAILS = "custom_details";
    public static final String DISPLAY_NAME = "display_name";
    public static final String EVENTS = "events";
    public static final String EVENT_ACTION = "event_action";
    public static final String EVENT_TYPE = "event_type";
    public static final String GROUP = "group";
    public static final String INVENTORY_URL = "inventory_url";
    public static final String ORG_ID = "org_id";
    public static final String PAYLOAD = "payload";
    public static final String SEVERITY = "severity";
    public static final String SOURCE = "source";
    public static final String SOURCE_NAMES = "source_names";
    public static final String SUMMARY = "summary";
    public static final String TIMESTAMP = "timestamp";

    // PagerDuty-specific date time formatter
    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * Extracts client links from the payload for PagerDuty integration.
     *
     * @param payload The input payload containing link information
     * @return JsonObject containing client links
     */
    public static JsonObject getClientLinks(JsonObject payload) {
        JsonObject links = new JsonObject();

        if (payload != null) {
            // Extract application URL if available
            String appUrl = payload.getString(APPLICATION_URL);
            if (appUrl != null) {
                links.put("application", appUrl);
            }

            // Extract inventory URL if available
            String inventoryUrl = payload.getString(INVENTORY_URL);
            if (inventoryUrl != null) {
                links.put("inventory", inventoryUrl);
            }
        }

        return links;
    }

    /**
     * Extracts source names from the source object.
     *
     * @param source The source object containing name information
     * @return JsonObject containing source names, or null if no valid names found
     */
    public static JsonObject getSourceNames(JsonObject source) {
        if (source == null) {
            return null;
        }

        JsonObject sourceNames = new JsonObject();
        boolean hasValidNames = false;

        // Extract display name if available
        String displayName = source.getString(DISPLAY_NAME);
        if (displayName != null && !displayName.trim().isEmpty()) {
            sourceNames.put("display_name", displayName);
            hasValidNames = true;
        }

        // Extract other common source name fields
        String name = source.getString("name");
        if (name != null && !name.trim().isEmpty()) {
            sourceNames.put("name", name);
            hasValidNames = true;
        }

        return hasValidNames ? sourceNames : null;
    }
}
