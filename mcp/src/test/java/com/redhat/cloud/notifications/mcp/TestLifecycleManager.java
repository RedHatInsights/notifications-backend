package com.redhat.cloud.notifications.mcp;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++  MCP TestLifecycleManager start +++");
        Map<String, String> properties = new HashMap<>();
        MockServerLifecycleManager.start();
        properties.put("quarkus.rest-client.notifications-backend.url", getMockServerUrl());
        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        MockServerLifecycleManager.stop();
    }
}
