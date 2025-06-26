package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import com.redhat.cloud.notifications.connector.http.UrlValidator;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;

@ApplicationScoped
public class PagerDutyCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String AUTHENTICATION = "authentication";
    public static final String URL = "url";
    public static final String PAGERDUTY_EVENT_V2_URL = "https://events.pagerduty.com/v2/enqueue";

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws Exception {

        String providedUrl = cloudEventData.getString(URL);
        if (providedUrl == null || providedUrl.isEmpty()) {
            exchange.setProperty(TARGET_URL, PAGERDUTY_EVENT_V2_URL);
        } else {
            exchange.setProperty(TARGET_URL, providedUrl);
        }

        UrlValidator.validateTargetUrl(exchange);

        JsonObject authentication = cloudEventData.getJsonObject(AUTHENTICATION);
        authenticationDataExtractor.extract(exchange, authentication);

        exchange.getIn().setBody(cloudEventData.getJsonObject(PAYLOAD));
    }
}
