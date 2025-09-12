package com.redhat.cloud.notifications.connector.email;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ Email TestLifecycleManager starting +++");
        Map<String, String> properties = new HashMap<>();
        // Set dummy URLs for BOP and recipients resolver since we're using mocks
        properties.put("notifications.connector.recipients-resolver.url", "http://localhost:8080");
        properties.put("quarkus.rest-client.recipients-resolver.url", "http://localhost:8080");
        properties.put("notifications.connector.user-provider.bop.url", "http://localhost:8080");
        properties.put("quarkus.rest-client.bop.url", "http://localhost:8080");
        return properties;
    }

    @Override
    public void stop() {
        System.out.println("++++ Email TestLifecycleManager stopped +++");
    }
}
