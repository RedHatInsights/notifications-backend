package com.redhat.cloud.notifications.connector.webhook;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ Webhook TestLifecycleManager starting +++");
        Map<String, String> properties = new HashMap<>();
        // Set dummy URLs for Webhook since we're using mocks
        properties.put("quarkus.rest-client.webhook-client.url", "http://localhost:8080");
        return properties;
    }

    @Override
    public void stop() {
        System.out.println("++++ Webhook TestLifecycleManager stopping +++");
    }
}
