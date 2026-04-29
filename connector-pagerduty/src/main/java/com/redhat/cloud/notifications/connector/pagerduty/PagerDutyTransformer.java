package com.redhat.cloud.notifications.connector.pagerduty;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class PagerDutyTransformer {

    public static final String PAYLOAD = "payload";

    public static final String ACCOUNT_ID = "account_id";
    public static final String APPLICATION = "application";
    public static final String APPLICATION_URL = "application_url";
    public static final String BUNDLE = "bundle";
    public static final String CLIENT = "client";
    public static final String CLIENT_URL = "client_url";
    public static final String CONTEXT = "context";
    public static final String CUSTOM_DETAILS = "custom_details";
    public static final String DISPLAY_NAME = "display_name";
    public static final String EVENT_ACTION = "event_action";
    public static final String EVENT_ACTION_TRIGGER = "trigger";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENTS = "events";
    public static final String GROUP = "group";
    public static final String HREF = "href";
    public static final String INVENTORY_URL = "inventory_url";
    public static final String LINKS = "links";
    public static final String ORG_ID = "org_id";
    public static final String RED_HAT_SEVERITY = "red_hat_severity";
    public static final String ROUTING_KEY = "routing_key";
    public static final String SEVERITY = "severity";
    public static final String SOURCE = "source";
    public static final String SOURCE_NAMES = "source_names";
    public static final String SUMMARY = "summary";
    public static final String TIMESTAMP = "timestamp";
    public static final String TEXT = "text";

    @Deprecated(forRemoval = true, since = "RHCLOUD-41561: after user-provided severity levels are removed.")
    public static final String PAGERDUTY_STATIC_SEVERITY = "pagerduty_static_severity";

    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+0000");

    private PagerDutyTransformer() {
    }

    static String buildPagerDutyPayload(JsonObject cloudEventPayload, String routingKey, boolean dynamicSeverityEnabled) {
        JsonObject message = new JsonObject();
        message.put(EVENT_ACTION, EVENT_ACTION_TRIGGER);
        message.mergeIn(getClientLinks(cloudEventPayload));

        JsonObject messagePayload = new JsonObject();
        messagePayload.put(SUMMARY, cloudEventPayload.getString(EVENT_TYPE));

        String timestamp = cloudEventPayload.getString(TIMESTAMP);
        try {
            messagePayload.put(TIMESTAMP, LocalDateTime.parse(timestamp).format(PD_DATE_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            Log.warnf(e, "Unable to parse timestamp %s, dropped from payload", timestamp);
        } catch (DateTimeException e) {
            Log.warnf(e, "Timestamp %s was successfully parsed, but could not be reformatted for PagerDuty, dropped from payload", timestamp);
        }

        messagePayload.put(SOURCE, cloudEventPayload.getString(APPLICATION));
        messagePayload.put(GROUP, cloudEventPayload.getString(BUNDLE));

        String orgId = cloudEventPayload.getString(ORG_ID);
        JsonObject customDetails = new JsonObject();
        customDetails.put(ACCOUNT_ID, cloudEventPayload.getString(ACCOUNT_ID));
        customDetails.put(ORG_ID, orgId);
        customDetails.put(CONTEXT, cloudEventPayload.getJsonObject(CONTEXT));

        JsonObject cloudSource = getSourceNames(cloudEventPayload.getJsonObject(SOURCE));
        if (cloudSource != null) {
            customDetails.put(SOURCE_NAMES, cloudSource);
        }

        messagePayload.put(SEVERITY, getSeverity(cloudEventPayload, dynamicSeverityEnabled));
        String redHatSeverity = cloudEventPayload.getString(SEVERITY);
        if (dynamicSeverityEnabled && redHatSeverity != null && !redHatSeverity.isEmpty()) {
            customDetails.put(RED_HAT_SEVERITY, redHatSeverity);
        }

        if (cloudEventPayload.containsKey(EVENTS)) {
            customDetails.put(EVENTS, cloudEventPayload.getJsonArray(EVENTS));
        }

        messagePayload.put(CUSTOM_DETAILS, customDetails);
        message.put(PAYLOAD, messagePayload);

        message.put(ROUTING_KEY, routingKey);

        return message.encode();
    }

    static void validatePayload(final JsonObject cloudEventPayload, String orgId) {
        String summary = cloudEventPayload.getString(EVENT_TYPE);
        if (summary == null || summary.isEmpty()) {
            throw new IllegalArgumentException("Event type must be specified for PagerDuty payload summary [orgId=" + orgId + "]");
        }

        String source = cloudEventPayload.getString(APPLICATION);
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Application must be specified for PagerDuty payload source [orgId=" + orgId + "]");
        }
    }

    static JsonObject getClientLinks(final JsonObject cloudEventPayload) {
        JsonObject clientLinks = new JsonObject();

        clientLinks.put(CLIENT, String.format("%s", cloudEventPayload.getString(APPLICATION)));
        clientLinks.put(CLIENT_URL, cloudEventPayload.getString(APPLICATION_URL));

        String inventoryUrl = cloudEventPayload.getString(INVENTORY_URL, "");
        if (!inventoryUrl.isEmpty()) {
            clientLinks.put(LINKS, JsonArray.of(
                    JsonObject.of(
                            HREF, inventoryUrl,
                            TEXT, "Host"
                    )
            ));
        }

        return clientLinks;
    }

    static JsonObject getSourceNames(final JsonObject cloudSource) {
        if (cloudSource != null) {
            JsonObject sourceNames = new JsonObject();

            JsonObject application = cloudSource.getJsonObject(APPLICATION);
            if (application != null) {
                sourceNames.put(APPLICATION, application.getString(DISPLAY_NAME));
            }
            JsonObject bundle = cloudSource.getJsonObject(BUNDLE);
            if (bundle != null) {
                sourceNames.put(BUNDLE, bundle.getString(DISPLAY_NAME));
            }
            JsonObject eventType = cloudSource.getJsonObject(EVENT_TYPE);
            if (eventType != null) {
                sourceNames.put(EVENT_TYPE, eventType.getString(DISPLAY_NAME));
            }

            if (!sourceNames.isEmpty()) {
                return sourceNames;
            }
        }

        return null;
    }

    static PagerDutySeverity getSeverity(final JsonObject cloudEventPayload, boolean dynamicSeverityEnabled) {
        String severity = cloudEventPayload.getString(SEVERITY);
        if (dynamicSeverityEnabled) {
            return PagerDutySeverity.fromSecuritySeverity(severity);
        } else {
            String staticSeverity = cloudEventPayload.getString(PAGERDUTY_STATIC_SEVERITY);
            if (staticSeverity != null && !staticSeverity.isEmpty()) {
                return PagerDutySeverity.fromJson(staticSeverity);
            } else {
                return PagerDutySeverity.fromJson(severity);
            }
        }
    }
}
