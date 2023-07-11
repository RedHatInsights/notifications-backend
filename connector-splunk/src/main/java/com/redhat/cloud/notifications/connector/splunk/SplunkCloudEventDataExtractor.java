package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.ProtocolException;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.AUTHENTICATION_TOKEN;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TRUST_ALL;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

@ApplicationScoped
public class SplunkCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String NOTIF_METADATA = "notif-metadata";
    public static final String SERVICES_COLLECTOR = "/services/collector";
    public static final String EVENT = "/event";
    public static final String SERVICES_COLLECTOR_EVENT = SERVICES_COLLECTOR + EVENT;
    public static final String RAW = "/raw";
    public static final String SERVICES_COLLECTOR_RAW = SERVICES_COLLECTOR + RAW;

    private static final UrlValidator HTTP_URL_VALIDATOR = new UrlValidator(new String[] {"http"}, ALLOW_LOCAL_URLS);
    private static final UrlValidator HTTPS_URL_VALIDATOR = new UrlValidator(new String[] {"https"}, ALLOW_LOCAL_URLS);

    @Override
    public void extract(Exchange exchange, JsonObject cloudEventData) throws Exception {

        // TODO Rely on the org ID parsing from IncomingCloudEventProcessor?
        exchange.setProperty(ORG_ID, cloudEventData.getString("org_id"));
        exchange.setProperty(ACCOUNT_ID, cloudEventData.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        exchange.setProperty(TARGET_URL, metadata.getString("url"));
        exchange.setProperty(AUTHENTICATION_TOKEN, metadata.getString("X-Insight-Token"));
        exchange.setProperty(TRUST_ALL, Boolean.valueOf(metadata.getString("trustAll")));
        cloudEventData.remove(NOTIF_METADATA);

        validateTargetUrl(exchange);
        fixTargetUrlPathIfNeeded(exchange);
        exchange.setProperty(TARGET_URL_NO_SCHEME, exchange.getProperty(TARGET_URL, String.class).replace("https://", ""));

        exchange.getIn().setBody(cloudEventData);
    }

    private void validateTargetUrl(Exchange exchange) throws Exception {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        if (HTTP_URL_VALIDATOR.isValid(targetUrl)) {
            throw new ProtocolException("HTTP protocol is not supported");
        } else if (!HTTPS_URL_VALIDATOR.isValid(targetUrl)) {
            throw new IllegalArgumentException("URL validation failed");
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
