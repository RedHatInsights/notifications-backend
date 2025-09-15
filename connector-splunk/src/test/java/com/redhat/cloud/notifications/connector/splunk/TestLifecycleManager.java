package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ SplunkTestLifecycleManager starting +++");
        MockServerLifecycleManager.start();
        Map<String, String> properties = new HashMap<>();
        // Configure mock URLs for testing
        properties.put("quarkus.rest-client.splunk.url", getMockServerUrl());
        return properties;
    }

    @Override
    public void stop() {
        MockServerLifecycleManager.stop();
        System.out.println("++++ SplunkTestLifecycleManager stopped +++");
    }
}
