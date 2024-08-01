package com.redhat.cloud.notifications.connector.pagerduty;

import io.vertx.core.json.JsonObject;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.AUTHENTICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.CUSTOM_DETAILS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.EVENT_ACTION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.METHOD;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.NOTIF_METADATA;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.PAYLOAD;

public class PagerDutyTestUtils {
    public static final String DEFAULT_HTTP_METHOD_POST = "POST";

    public static final String DEFAULT_EVENT_ACTION = "trigger";

    public static final String DEFAULT_SUMMARY = "default-payload-summary";
    // TODO update to match default as set in PagerDutyTransformer (RHCLOUD-33788)
    public static final String DEFAULT_SEVERITY = "warning";
    public static final String DEFAULT_SOURCE = "default-payload-source";

    static JsonObject createCloudEventData(String url, boolean trustAll) {
        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", url);
        metadata.put(TRUST_ALL, Boolean.toString(trustAll));
        metadata.put(METHOD, DEFAULT_HTTP_METHOD_POST);
        metadata.put(AUTHENTICATION, authentication);

        JsonObject custom_details = new JsonObject();
        custom_details.put("org_id", DEFAULT_ORG_ID);
        custom_details.put("account_id", DEFAULT_ACCOUNT_ID);

        JsonObject payload = new JsonObject();
        payload.put("summary", DEFAULT_SUMMARY);
        payload.put("severity", DEFAULT_SEVERITY);
        payload.put("source", DEFAULT_SOURCE);
        payload.put(CUSTOM_DETAILS, custom_details);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put(EVENT_ACTION, DEFAULT_EVENT_ACTION);
        cloudEventData.put(PAYLOAD, payload);
        cloudEventData.put(NOTIF_METADATA, metadata);

        return cloudEventData;
    }
}
