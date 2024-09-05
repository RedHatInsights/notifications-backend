package com.redhat.cloud.notifications.connector.pagerduty;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    public static final String ORG_ID = "org_id";
    public static final String SEVERITY = "severity";
    public static final String SOURCE = "source";
    public static final String SOURCE_NAMES = "source_names";
    public static final String SUMMARY = "summary";
    public static final String TIMESTAMP = "timestamp";

    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Override
    public void process(Exchange exchange) {
        JsonObject cloudEventData = exchange.getIn().getBody(JsonObject.class);

        JsonObject cloudEventPayload = cloudEventData.getJsonObject(PAYLOAD);
        JsonObject message = new JsonObject();
        message.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
        message.mergeIn(getClientLink(cloudEventPayload, cloudEventData.getString(ENVIRONMENT_URL)));

        JsonObject messagePayload = new JsonObject();
        messagePayload.put(SUMMARY, cloudEventPayload.getString(EVENT_TYPE));
        messagePayload.put(TIMESTAMP, LocalDateTime.parse(cloudEventPayload.getString(TIMESTAMP)).format(PD_DATE_TIME_FORMATTER));
        // TODO read from properties
        messagePayload.put(SEVERITY, PagerDutySeverity.WARNING);
        messagePayload.put(SOURCE, cloudEventPayload.getString(APPLICATION));
        messagePayload.put(GROUP, cloudEventPayload.getString(BUNDLE));

        JsonObject customDetails = new JsonObject();
        customDetails.put(ACCOUNT_ID, cloudEventPayload.getString(ACCOUNT_ID));
        customDetails.put(ORG_ID, cloudEventPayload.getString(ORG_ID));
        customDetails.put(CONTEXT, JsonObject.mapFrom(cloudEventPayload.getJsonObject(CONTEXT)));
        customDetails.put(SOURCE_NAMES, JsonObject.mapFrom(cloudEventPayload.getJsonObject(SOURCE)));
        // Keep events, if provided
        if (cloudEventPayload.containsKey(EVENTS)) {
            customDetails.put(EVENTS, cloudEventPayload.getJsonArray(EVENTS));
        }

        messagePayload.put(CUSTOM_DETAILS, customDetails);
        message.put(PAYLOAD, messagePayload);

        exchange.getIn().setBody(message.encode());
    }

    /**
     * Adapted from CamelProcessor template for Teams
     * <br>
     * TODO update to work more consistently and with other platforms
     *
     * @return {@link #CLIENT} and {@link #CLIENT_URL}
     */
    private JsonObject getClientLink(JsonObject cloudEventPayload, String environmentUrl) {
        JsonObject clientLink = new JsonObject();
        String contextName = cloudEventPayload.getJsonObject(CONTEXT).getString(DISPLAY_NAME);

        if (contextName != null) {
            clientLink.put(CLIENT, contextName);
            clientLink.put(CLIENT_URL, String.format("%s/insights/inventory/%s",
                    environmentUrl,
                    cloudEventPayload.getJsonObject(CONTEXT).getString("inventory_id")
            ));
        } else {
            clientLink.put(CLIENT, String.format("Open %s", cloudEventPayload.getString(APPLICATION)));
            clientLink.put(CLIENT_URL, String.format("%s/insights/%s",
                    environmentUrl,
                    cloudEventPayload.getString(APPLICATION)
            ));
        }

        return clientLink;
    }
}
