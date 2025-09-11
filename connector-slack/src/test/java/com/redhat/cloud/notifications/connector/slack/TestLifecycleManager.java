package com.redhat.cloud.notifications.connector.slack;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ Slack TestLifecycleManager starting +++");
        Map<String, String> properties = new HashMap<>();
        // Set dummy URLs for Slack since we're using mocks
        properties.put("quarkus.rest-client.slack-client.url", "http://localhost:8080");
        return properties;
    }

    @Override
    public void stop() {
        System.out.println("++++ Slack TestLifecycleManager stopping +++");
    }
}
