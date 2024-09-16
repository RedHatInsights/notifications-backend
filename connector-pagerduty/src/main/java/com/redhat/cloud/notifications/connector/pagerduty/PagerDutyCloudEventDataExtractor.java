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
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

@ApplicationScoped
public class PagerDutyCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String AUTHENTICATION = "authentication";
    public static final String URL = "url";
    public static final String PAGERDUTY_EVENT_V2_URL = "https://events.pagerduty.com/v2/enqueue";

    // HTTP URLs (or disabling SSL verification) must be permitted to run test cases
    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"}, ALLOW_LOCAL_URLS);

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws IllegalArgumentException {

        validatePayload(cloudEventData);

        String providedUrl = cloudEventData.getString(TARGET_URL);
        if (providedUrl == null || providedUrl.isEmpty()) {
            exchange.setProperty(TARGET_URL, cloudEventData.getString(PAGERDUTY_EVENT_V2_URL));
        } else {
            exchange.setProperty(TARGET_URL, providedUrl);
        }

        JsonObject authentication = cloudEventData.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        exchange.getIn().setBody(cloudEventData.getJsonObject(PAYLOAD));
    }

    private void validatePayload(JsonObject cloudEventData) {
        String endpointUrl = cloudEventData.getString(URL);
        if (endpointUrl == null) {
            throw new MissingResourceException("The endpoint URL is required", PagerDutyCloudEventDataExtractor.class.getName(), "url");
        } else if (!URL_VALIDATOR.isValid(endpointUrl)) {
            throw new IllegalArgumentException("URL validation failed");
        }
    }
}
