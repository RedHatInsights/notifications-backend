package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.http.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TRUST_ALL;

@ApplicationScoped
public class SplunkCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String NOTIF_METADATA = "notif-metadata";
    public static final String SERVICES_COLLECTOR = "/services/collector";
    public static final String EVENT = "/event";
    public static final String SERVICES_COLLECTOR_EVENT = SERVICES_COLLECTOR + EVENT;
    public static final String RAW = "/raw";
    public static final String SERVICES_COLLECTOR_RAW = SERVICES_COLLECTOR + RAW;
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws Exception {

        exchange.setProperty(ACCOUNT_ID, cloudEventData.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        exchange.setProperty(TARGET_URL, metadata.getString("url"));
        exchange.setProperty(TRUST_ALL, Boolean.valueOf(metadata.getString("trustAll")));

        JsonObject authentication = metadata.getJsonObject("authentication");
        authenticationDataExtractor.extract(exchange, authentication);

        cloudEventData.remove(NOTIF_METADATA);

        validateTargetUrl(exchange);
        fixTargetUrlPathIfNeeded(exchange);
        exchange.setProperty(TARGET_URL_NO_SCHEME, exchange.getProperty(TARGET_URL, String.class).replace("https://", ""));

        exchange.getIn().setBody(cloudEventData);
    }

    private void validateTargetUrl(Exchange exchange) throws Exception {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        try {
            String scheme = (new URI(targetUrl)).getScheme();

            if (HTTP_SCHEME.equalsIgnoreCase(scheme)) {
                throw new ProtocolException("HTTP protocol is not supported");
            } else if (!HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("URL validation failed");
            }
            // handle case where url is null (should never happen)
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl);
        }
    }

    private void fixTargetUrlPathIfNeeded(Exchange exchange) {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }
        if (!targetUrl.endsWith(SERVICES_COLLECTOR_EVENT)) {
            if (targetUrl.endsWith(SERVICES_COLLECTOR_RAW)) {
                targetUrl = targetUrl.substring(0, targetUrl.length() - RAW.length()) + EVENT;
            } else if (targetUrl.endsWith(SERVICES_COLLECTOR)) {
                targetUrl += EVENT;
            } else {
                targetUrl += SERVICES_COLLECTOR_EVENT;
            }
            exchange.setProperty(TARGET_URL, targetUrl);
        }
    }
}
