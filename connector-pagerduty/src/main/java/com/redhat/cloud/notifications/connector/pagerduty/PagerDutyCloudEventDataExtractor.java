package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.commons.validator.routines.UrlValidator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.MissingResourceException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

@ApplicationScoped
public class PagerDutyCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String AUTHENTICATION = "authentication";
    public static final String URL = "url";
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

    // HTTP URLs (or disabling SSL verification) must be permitted to run test cases
    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"}, ALLOW_LOCAL_URLS);
    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws IllegalArgumentException {

        validatePayload(cloudEventData);

        exchange.setProperty(TARGET_URL, cloudEventData.getString(URL));

        JsonObject authentication = cloudEventData.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        JsonObject pagerDutyMessage = transformPagerDutyMessage(cloudEventData);

        exchange.getIn().setBody(pagerDutyMessage.encode());
    }

    /* TODO update the test cases */
    private void validatePayload(JsonObject cloudEventData) {
        String endpointUrl = cloudEventData.getString(URL);
        if (endpointUrl == null) {
            throw new MissingResourceException("The endpoint URL is required", PagerDutyCloudEventDataExtractor.class.getName(), "url");
        } else if (!URL_VALIDATOR.isValid(endpointUrl)) {
            throw new IllegalArgumentException("URL validation failed");
        }
    }

    /**
     * Constructs a <a href="https://support.pagerduty.com/main/docs/pd-cef">PD-CEF</a> alert event.
     * <br>
     * The severity is set to {@link PagerDutySeverity#WARNING}, and the action to {@link PagerDutyEventAction#TRIGGER} for
     * now. The following optional fields are not set (in jq format): <code>.payload.component, .payload.class, .dedup_key, .links[], .trigger[]</code>
     * <br>
     * <ul>
     *     <li>TODO determine which details to include/remove</li>
     * </ul>
     */
    private JsonObject transformPagerDutyMessage(JsonObject cloudEventData) {
        JsonObject cloudEventPayload = cloudEventData.getJsonObject(PAYLOAD);
        JsonObject message = new JsonObject();
        // TODO is it possible for tenant apps to indicate if acknowledge or resolve should be sent?
        message.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
        message.mergeIn(getClientLink(cloudEventPayload, cloudEventData.getString(ENVIRONMENT_URL)));

        JsonObject messagePayload = new JsonObject();
        messagePayload.put(SUMMARY, cloudEventPayload.getString(EVENT_TYPE));
        messagePayload.put(TIMESTAMP, LocalDateTime.parse(cloudEventPayload.getString(TIMESTAMP)).format(PD_DATE_TIME_FORMATTER));
        // TODO is it possible for tenant apps to indicate what severity to use?
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


        return message;
    }

    /**
     * Adapted from CamelProcessor template for Teams
     * <br>
     * TODO should this be a template in notifications-engine for maintainability?
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
