package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.AUTHENTICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.APPLICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.APPLICATION_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.BUNDLE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CLIENT;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CLIENT_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CONTEXT;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CUSTOM_DETAILS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.DISPLAY_NAME;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ENVIRONMENT_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENTS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENT_ACTION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.GROUP;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.INVENTORY_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PD_DATE_TIME_FORMATTER;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SEVERITY;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SOURCE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SOURCE_NAMES;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SUMMARY;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.TIMESTAMP;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.getClientLinks;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.getSourceNames;

public class PagerDutyTestUtils {

    static final String DEFAULT_ENVIRONMENT_URL = "https://console.redhat.com";

    static JsonObject createCloudEventData(String url) {
        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("url", url);
        cloudEventData.put(AUTHENTICATION, authentication);

        return cloudEventData;
    }

    static JsonObject createIncomingPayload(String url) {
        JsonObject cloudEventData = createCloudEventData(url);
        return createIncomingPayload(cloudEventData);
    }

    static JsonObject createIncomingPayload(JsonObject cloudEventData) {
        JsonObject payload = new JsonObject();
        payload.put(ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
        payload.put(APPLICATION, "default-application");
        payload.put(BUNDLE, "default-bundle");
        payload.put(CONTEXT, JsonObject.of(
                DISPLAY_NAME, "console",
                "inventory_id", "8a4a4f75-5319-4255-9eb5-1ee5a92efd7f"
        ));
        payload.put(EVENT_TYPE, "default-event-type");
        payload.put(EVENTS, JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value"),
                JsonObject.of("event-3-key", "event-3-value"),
                JsonObject.of("event-4-with-metadata-and-payload", JsonObject.of(
                        "metadata", "some metadata could be placed here",
                        "payload", "Here is a test payload message"
                ))
        ));
        payload.put(ORG_ID, DEFAULT_ORG_ID);
        payload.put(TIMESTAMP, LocalDateTime.of(2024, 8, 12, 17, 26, 19).toString());

        JsonObject source = JsonObject.of(
                APPLICATION, JsonObject.of(DISPLAY_NAME, "Default Application Name"),
                BUNDLE, JsonObject.of(DISPLAY_NAME, "Default Bundle Name"),
                EVENT_TYPE, JsonObject.of(DISPLAY_NAME, "Default Event Type Name")
        );

        payload.put(SOURCE, source);
        payload.put(ENVIRONMENT_URL, "https://console.redhat.com");
        InsightsUrlsBuilder.buildInventoryUrl(payload)
                .ifPresent(url -> payload.put(INVENTORY_URL, url));
        payload.put(APPLICATION_URL, InsightsUrlsBuilder.buildApplicationUrl(payload));
        payload.put(SEVERITY, PagerDutySeverity.WARNING);
        cloudEventData.put(PAYLOAD, payload);

        return cloudEventData;
    }

    static JsonObject buildExpectedOutgoingPayload(final JsonObject incoming) {
        JsonObject expected = incoming.copy();
        expected.remove(PagerDutyCloudEventDataExtractor.URL);
        expected.remove(PagerDutyCloudEventDataExtractor.AUTHENTICATION);

        JsonObject oldInnerPayload = expected.getJsonObject(PAYLOAD);
        expected.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
        expected.mergeIn(getClientLink(oldInnerPayload, oldInnerPayload.getString(ENVIRONMENT_URL)));
        expected.mergeIn(getClientLinks(oldInnerPayload));

        JsonObject newInnerPayload = new JsonObject();
        newInnerPayload.put(SUMMARY, oldInnerPayload.getString(EVENT_TYPE));

        String timestamp = oldInnerPayload.getString(TIMESTAMP);
        if (timestamp != null) {
            try {
                newInnerPayload.put(TIMESTAMP, LocalDateTime.parse(timestamp).format(PD_DATE_TIME_FORMATTER));
            } catch (DateTimeException ignored) {
                // Not added
            }
        }

        newInnerPayload.put(SEVERITY, PagerDutySeverity.fromJson(oldInnerPayload.getString(SEVERITY)));
        newInnerPayload.put(SOURCE, oldInnerPayload.getString(APPLICATION));
        newInnerPayload.put(GROUP, oldInnerPayload.getString(BUNDLE));

        JsonObject customDetails = JsonObject.of(
                ACCOUNT_ID, DEFAULT_ACCOUNT_ID,
                ORG_ID, DEFAULT_ORG_ID,
                CONTEXT, oldInnerPayload.getJsonObject(CONTEXT)
        );
        JsonObject newInnerSourceNames = getSourceNames(oldInnerPayload.getJsonObject(SOURCE));
        if (newInnerSourceNames != null) {
            customDetails.put(SOURCE_NAMES, newInnerSourceNames);
        }

        if (oldInnerPayload.containsKey(EVENTS)) {
            customDetails.put(EVENTS, oldInnerPayload.getJsonArray(EVENTS));
        }

        newInnerPayload.put(CUSTOM_DETAILS, customDetails);
        expected.remove(PAYLOAD);
        expected.put(PAYLOAD, newInnerPayload);
        return expected;
    }

    private static JsonObject getClientLink(final JsonObject oldInnerPayload, String environmentUrl) {
        JsonObject clientLink = new JsonObject();

        String contextName = oldInnerPayload.containsKey(CONTEXT)
                ? oldInnerPayload.getJsonObject(CONTEXT).getString(DISPLAY_NAME)
                : null;

        if (contextName != null) {
            clientLink.put(CLIENT, contextName);

            String inventoryId = oldInnerPayload.getJsonObject(CONTEXT).getString("inventory_id");
            if (environmentUrl != null && !environmentUrl.isEmpty() && inventoryId != null && !inventoryId.isEmpty()) {
                clientLink.put(CLIENT_URL, String.format("%s/insights/inventory/%s",
                        environmentUrl,
                        oldInnerPayload.getJsonObject(CONTEXT).getString("inventory_id")
                ));
            }
        } else {
            if (environmentUrl != null && !environmentUrl.isEmpty()) {
                clientLink.put(CLIENT, String.format("Open %s", oldInnerPayload.getString(APPLICATION)));
                clientLink.put(CLIENT_URL, String.format("%s/insights/%s",
                        environmentUrl,
                        oldInnerPayload.getString(APPLICATION)
                ));
            } else {
                clientLink.put(CLIENT, oldInnerPayload.getString(APPLICATION));
            }
        }

        return clientLink;
    }
}

/**
 * This class emulates the functionality of the same class within notifications-engine.
 */
class InsightsUrlsBuilder {
    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>An inventory URL will only be generated if fields from one of these two formats are present:</p>
     *
     * <ul>
     *     <li>{@code { "context": { "host_url": "non_empty_string" }}}</li>
     *     <li>{@code { "context": { "inventory_id": "non_empty_string" }}}</li>
     *     <li>{@code { "context": { "display_name": "non_empty_string" } }}</li>
     * </ul>
     *
     * <p>If neither field is present, an {@link Optional#empty()} will be returned. If expected fields of
     * {@link Action#getBundle()} or {@link Action#getApplication()} are missing, an inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by {@code BaseTransformer#toJsonObject(Event)}
     * @return URL to the generating inventory item, if required fields are present
     */
    static Optional<String> buildInventoryUrl(JsonObject data) {
        String path;
        ArrayList<String> queryParamParts = new ArrayList<>();
        JsonObject context = data.getJsonObject("context");
        if (context == null) {
            return Optional.empty();
        }

        // A provided host url does not need to be modified
        String host_url = context.getString("host_url", "");
        if (!host_url.isEmpty()) {
            return Optional.of(host_url);
        }

        String inventoryId = context.getString("inventory_id", "");
        String displayName = context.getString("display_name", "");

        if (!displayName.isEmpty()) {
            if (data.getString("bundle", "").equals("openshift")
                    && data.getString("application", "").equals("advisor")) {
                path = String.format("/openshift/insights/advisor/clusters/%s", displayName);
            } else {
                path = "/insights/inventory/";
                if (!inventoryId.isEmpty()) {
                    path += inventoryId;
                } else {
                    queryParamParts.add(String.format("hostname_or_id=%s", displayName));
                }
            }
        } else {
            return Optional.empty();
        }

        if (!queryParamParts.isEmpty()) {
            String queryParams = "?" + String.join("&", queryParamParts);
            path += queryParams;
        }

        return Optional.of(PagerDutyTestUtils.DEFAULT_ENVIRONMENT_URL + path);
    }

    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>If the expected fields {@link Action#getApplication()} and {@link Action#getBundle()} are not present, an
     * inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by {@code BaseTransformer#toJsonObject(Event)}
     * @return URL to the generating application
     */
    static String buildApplicationUrl(JsonObject data) {
        String path = "";

        String bundle = data.getString("bundle", "");
        String application = data.getString("application", "");

        if (bundle.equals("openshift")) {
            path = "openshift/";
        }

        if (application.equals("integrations")) {
            path += "settings/";
        } else {
            path += "insights/";
        }

        path += application;

        return String.format("%s/%s", PagerDutyTestUtils.DEFAULT_ENVIRONMENT_URL, path);
    }
}
