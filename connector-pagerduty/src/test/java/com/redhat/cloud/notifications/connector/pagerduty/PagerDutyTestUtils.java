package com.redhat.cloud.notifications.connector.pagerduty;

import io.vertx.core.json.JsonObject;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyCloudEventDataExtractor.AUTHENTICATION;

public class PagerDutyTestUtils {

    static JsonObject createCloudEventData(String url) {
        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("url", url);
        cloudEventData.put(AUTHENTICATION, authentication);

        return cloudEventData;
    }
}
