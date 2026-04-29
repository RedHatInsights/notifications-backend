package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;

import java.util.Map;

public class PagerDutyTestLifecycleManager extends TestLifecycleManager {

    @Override
    public Map<String, String> start() {
        super.start();
        return Map.of(
            "quarkus.rest-client.connector-rest-client.url",
            MockServerLifecycleManager.getMockServerHttpsUrl() + "/v2/enqueue"
        );
    }
}
