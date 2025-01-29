package com.redhat.cloud.notifications.connector.pagerduty;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Constructs a <a href="https://support.pagerduty.com/main/docs/pd-cef">PD-CEF</a> alert event.
 * <br>
 * The severity is set to {@link PagerDutySeverity#WARNING}, and the action to {@link PagerDutyEventAction#TRIGGER} for
 * now. The following optional fields are not set (in jq format): <code>.payload.component, .payload.class, .dedup_key, .links[], .trigger[]</code>
 * <br>
 */
@ApplicationScoped
public class PagerDutyTransformer implements Processor {

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
    public static final String ENVIRONMENT_URL = "environment_url";
    public static final String EVENT_ACTION = "event_action";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENTS = "events";
    public static final String GROUP = "group";
    public static final String HREF = "href";
    public static final String INVENTORY_URL = "inventory_url";
    public static final String LINKS = "links";
    public static final String ORG_ID = "org_id";
    public static final String SEVERITY = "severity";
    public static final String SOURCE = "source";
    public static final String SOURCE_NAMES = "source_names";
    public static final String SUMMARY = "summary";
    public static final String TIMESTAMP = "timestamp";
    public static final String TEXT = "text";

    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+0000");

    @Override
    public void process(Exchange exchange) {
        JsonObject cloudEventPayload = exchange.getIn().getBody(JsonObject.class);
        validatePayload(cloudEventPayload);

        JsonObject message = new JsonObject();
        message.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
        message.mergeIn(getClientLink(cloudEventPayload, cloudEventPayload.getString(ENVIRONMENT_URL)));
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

        messagePayload.put(SEVERITY, PagerDutySeverity.fromJson(cloudEventPayload.getString(SEVERITY)));
        messagePayload.put(SOURCE, cloudEventPayload.getString(APPLICATION));
        messagePayload.put(GROUP, cloudEventPayload.getString(BUNDLE));

        JsonObject customDetails = new JsonObject();
        customDetails.put(ACCOUNT_ID, cloudEventPayload.getString(ACCOUNT_ID));
        customDetails.put(ORG_ID, cloudEventPayload.getString(ORG_ID));
        customDetails.put(CONTEXT, cloudEventPayload.getJsonObject(CONTEXT));

        // Add source names, if provided
        JsonObject cloudSource = getSourceNames(cloudEventPayload.getJsonObject(SOURCE));
        if (cloudSource != null) {
            customDetails.put(SOURCE_NAMES, cloudSource);
        }

        // Keep events, if provided
        if (cloudEventPayload.containsKey(EVENTS)) {
            customDetails.put(EVENTS, cloudEventPayload.getJsonArray(EVENTS));
        }

        messagePayload.put(CUSTOM_DETAILS, customDetails);
        message.put(PAYLOAD, messagePayload);

        exchange.getIn().setBody(message.encode());
    }

    /**
     * Validates that the inputs for the required Alert Event fields are present
     */
    private void validatePayload(final JsonObject cloudEventPayload) {
        String summary = cloudEventPayload.getString(EVENT_TYPE);
        if (summary == null || summary.isEmpty()) {
            throw new IllegalArgumentException("Event type must be specified for PagerDuty payload summary");
        }

        String source = cloudEventPayload.getString(APPLICATION);
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Application must be specified for PagerDuty payload source");
        }

        String severity = cloudEventPayload.getString(SEVERITY);
        if (severity == null || severity.isEmpty()) {
            throw new IllegalArgumentException("Severity must be specified for PagerDuty payload");
        } else {
            try {
                PagerDutySeverity.fromJson(severity);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid severity value provided for PagerDuty payload: " + severity);
            }
        }
    }

    /**
     * Adapted from CamelProcessor template for Teams, with some changes to more gracefully handle missing fields
     * <br>
     * TODO update to work more consistently and with other platforms
     *
     * @return {@link #CLIENT} and {@link #CLIENT_URL}
     */
    private JsonObject getClientLink(final JsonObject cloudEventPayload, String environmentUrl) {
        JsonObject clientLink = new JsonObject();

        String contextName = cloudEventPayload.containsKey(CONTEXT)
                ? cloudEventPayload.getJsonObject(CONTEXT).getString(DISPLAY_NAME)
                : null;

        if (contextName != null) {
            clientLink.put(CLIENT, contextName);

            String inventoryId = cloudEventPayload.getJsonObject(CONTEXT).getString("inventory_id");
            if (environmentUrl != null && !environmentUrl.isEmpty() && inventoryId != null && !inventoryId.isEmpty()) {
                clientLink.put(CLIENT_URL, String.format("%s/insights/inventory/%s",
                        environmentUrl,
                        cloudEventPayload.getJsonObject(CONTEXT).getString("inventory_id")
                ));
            }
        } else {
            if (environmentUrl != null && !environmentUrl.isEmpty()) {
                clientLink.put(CLIENT, String.format("Open %s", cloudEventPayload.getString(APPLICATION)));
                clientLink.put(CLIENT_URL, String.format("%s/insights/%s",
                        environmentUrl,
                        cloudEventPayload.getString(APPLICATION)
                ));
            } else {
                clientLink.put(CLIENT, cloudEventPayload.getString(APPLICATION));
            }
        }

        return clientLink;
    }

    /**
     * Performs the following link conversions:
     * <ul>
     *     <li>{@link #APPLICATION} integrated into {@link #CLIENT}</li>
     *     <li>{@link #APPLICATION_URL} becomes {@link #CLIENT_URL}</li>
     *     <li>{@link #INVENTORY_URL}, if present, creates an entry in the {@link #LINKS} object</li>
     * </ul>
     * <p>
     * The result is similar to the links provided in Microsoft Teams notifications.
     */
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
}
