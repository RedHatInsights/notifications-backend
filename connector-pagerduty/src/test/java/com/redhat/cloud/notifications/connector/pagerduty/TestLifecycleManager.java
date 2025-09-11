package com.redhat.cloud.notifications.connector.pagerduty;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ PagerDuty TestLifecycleManager starting +++");
        Map<String, String> properties = new HashMap<>();
        // Set dummy URLs for PagerDuty since we're using mocks
        properties.put("notifications.connector.pagerduty.url", "http://localhost:8080");
        properties.put("quarkus.rest-client.pagerduty.url", "http://localhost:8080");
        return properties;
    }

    @Override
    public void stop() {
        System.out.println("++++ PagerDuty TestLifecycleManager stopped +++");
    }
}
