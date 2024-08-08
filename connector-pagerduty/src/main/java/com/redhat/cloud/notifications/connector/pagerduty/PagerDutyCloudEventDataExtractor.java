package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpMethods;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.ProtocolException;

import java.util.MissingResourceException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TARGET_URL_NO_SCHEME;

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

    private static final UrlValidator HTTPS_URL_VALIDATOR = new UrlValidator(new String[]{"https"});
    private static final UrlValidator HTTP_URL_VALIDATOR = new UrlValidator(new String[]{"http"});

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws IllegalArgumentException, ProtocolException {

        validatePayload(cloudEventData);

        JsonObject customDetails = cloudEventData.getJsonObject(PAYLOAD).getJsonObject(CUSTOM_DETAILS);
        exchange.setProperty(ACCOUNT_ID, customDetails.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        exchange.setProperty(TARGET_URL, metadata.getString(URL));
        validateTargetUrl(exchange);
        exchange.setProperty(TARGET_URL_NO_SCHEME, exchange.getProperty(TARGET_URL, String.class).replace("https://", ""));
        exchange.setProperty(TRUST_ALL, Boolean.valueOf(metadata.getString("trustAll")));

        JsonObject authentication = metadata.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        cloudEventData.remove(NOTIF_METADATA);

        exchange.getIn().setBody(cloudEventData.encode());
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.valueOf(metadata.getString(METHOD)));
    }

    private void validatePayload(JsonObject cloudEventData) {
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

    private void validateTargetUrl(Exchange exchange) throws IllegalArgumentException, ProtocolException {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        if (HTTP_URL_VALIDATOR.isValid(targetUrl)) {
            throw new ProtocolException("HTTP protocol is not supported");
        } else if (!HTTPS_URL_VALIDATOR.isValid(targetUrl)) {
            throw new IllegalArgumentException("URL validation failed");
        }
    }
}
