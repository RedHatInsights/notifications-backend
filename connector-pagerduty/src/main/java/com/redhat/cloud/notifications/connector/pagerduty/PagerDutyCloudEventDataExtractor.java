package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.MissingResourceException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.ACCOUNT_ID;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

@ApplicationScoped
public class PagerDutyCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String NOTIF_METADATA = "notif-metadata";
    public static final String AUTHENTICATION = "authentication";
    public static final String METHOD = "method";
    public static final String TRUST_ALL = "trustAll";
    public static final String URL = "url";

    public static final String PAYLOAD = "payload";
    public static final String CUSTOM_DETAILS = "custom_details";
    public static final String EVENT_ACTION = "event_action";

    // HTTP URLs (or disabling SSL verification) must be permitted to run test cases
    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"}, ALLOW_LOCAL_URLS);

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws IllegalArgumentException {

        validatePayload(cloudEventData);

        JsonObject customDetails = cloudEventData.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS);
        exchange.setProperty(ACCOUNT_ID, customDetails.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        exchange.setProperty(TARGET_URL, metadata.getString(URL));
        exchange.setProperty(TRUST_ALL, Boolean.valueOf(metadata.getString("trustAll")));

        JsonObject authentication = metadata.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        cloudEventData.remove(NOTIF_METADATA);

        exchange.getIn().setBody(cloudEventData.encode());
    }

    private void validatePayload(JsonObject cloudEventData) {
        String endpointUrl = cloudEventData.getJsonObject(NOTIF_METADATA).getString(URL);
        if (endpointUrl == null) {
            throw new MissingResourceException("The endpoint URL is required", PagerDutyCloudEventDataExtractor.class.getName(), NOTIF_METADATA + ".url");
        } else if (!URL_VALIDATOR.isValid(endpointUrl)) {
            throw new IllegalArgumentException("URL validation failed");
        }

        if (cloudEventData.getJsonObject(PAYLOAD) == null) {
            throw new MissingResourceException("The '" + PAYLOAD + "' field is required", PagerDutyCloudEventDataExtractor.class.getName(), PAYLOAD);
        } else {
            if (cloudEventData.getJsonObject(PAYLOAD).getString("summary") == null) {
                throw new MissingResourceException("The alert summary field is required", PagerDutyCloudEventDataExtractor.class.getName(), PAYLOAD + ".summary");
            }
            if (cloudEventData.getJsonObject(PAYLOAD).getString("severity") == null) {
                throw new MissingResourceException("The alert severity field is required", PagerDutyCloudEventDataExtractor.class.getName(), PAYLOAD + ".severity");
            }
            if (cloudEventData.getJsonObject(PAYLOAD).getString("source") == null) {
                throw new MissingResourceException("The alert source field is required", PagerDutyCloudEventDataExtractor.class.getName(), PAYLOAD + ".source");
            }
        }

        if (cloudEventData.getString(EVENT_ACTION) == null) {
            throw new MissingResourceException("The '" + EVENT_ACTION + "' field is required", PagerDutyCloudEventDataExtractor.class.getName(), EVENT_ACTION);
        }
    }
}
