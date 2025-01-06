package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ TestLifecycleManager starting +++");
        MockServerLifecycleManager.start();
        Map<String, String> properties = new HashMap<>();
        properties.put("notifications.connector.recipients-resolver.url", getMockServerUrl());
        properties.put("quarkus.rest-client.recipients-resolver.url", getMockServerUrl());
        properties.put("notifications.connector.user-provider.bop.url", getMockServerUrl());
        properties.put("quarkus.rest-client.bop.url", getMockServerUrl());
        return properties;
    }

    @Override
    public void stop() {
        MockServerLifecycleManager.stop();
        System.out.println("++++ TestLifecycleManager stopped +++");
    }
}
