package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Collections;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++ TestLifecycleManager starting +++");
        MockServerLifecycleManager.start();
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        MockServerLifecycleManager.stop();
        System.out.println("++++ TestLifecycleManager stopped +++");
    }
}
