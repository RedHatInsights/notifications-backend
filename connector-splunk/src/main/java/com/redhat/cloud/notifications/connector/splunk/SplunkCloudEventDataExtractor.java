package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.CloudEventDataExtractor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import com.redhat.cloud.notifications.connector.http.UrlValidator;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Splunk-specific CloudEvent data extractor.
 * This is the new version that replaces the Camel-based SplunkCloudEventDataExtractor.
 */
@ApplicationScoped
public class SplunkCloudEventDataExtractor extends CloudEventDataExtractor {

    public static final String NOTIF_METADATA = "notif-metadata";
    public static final String SERVICES_COLLECTOR = "/services/collector";
    public static final String EVENT = "/event";
    public static final String SERVICES_COLLECTOR_EVENT = SERVICES_COLLECTOR + EVENT;
    public static final String RAW = "/raw";
    public static final String SERVICES_COLLECTOR_RAW = SERVICES_COLLECTOR + RAW;

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Inject
    UrlValidator urlValidator;

    @Override
    public void extract(ExceptionProcessor.ProcessingContext context, JsonObject cloudEventData) throws Exception {

        context.setAdditionalProperty("ACCOUNT_ID", cloudEventData.getString("account_id"));

        JsonObject metadata = cloudEventData.getJsonObject(NOTIF_METADATA);
        if (metadata != null) {
            String targetUrl = metadata.getString("url");
            context.setTargetUrl(targetUrl);
            context.setAdditionalProperty("TRUST_ALL", Boolean.valueOf(metadata.getString("trustAll")));

            JsonObject authentication = metadata.getJsonObject("authentication");
            authenticationDataExtractor.extract(context, authentication);

            // Remove metadata from the data so it's not sent to Splunk
            cloudEventData.remove(NOTIF_METADATA);
        }

        // Validate and fix the target URL
        urlValidator.validateTargetUrl(context);
        fixTargetUrlPathIfNeeded(context);

        String targetUrlNoScheme = context.getTargetUrl().replace("https://", "");
        context.setAdditionalProperty("TARGET_URL_NO_SCHEME", targetUrlNoScheme);

        // Store the processed cloud event data
        JsonObject originalCloudEvent = context.getOriginalCloudEvent();
        if (originalCloudEvent != null) {
            originalCloudEvent.put("data", cloudEventData);
        }
    }

    private void fixTargetUrlPathIfNeeded(ExceptionProcessor.ProcessingContext context) {
        String targetUrl = context.getTargetUrl();
        if (targetUrl == null) {
            return;
        }

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
            context.setTargetUrl(targetUrl);
        }
    }
}
