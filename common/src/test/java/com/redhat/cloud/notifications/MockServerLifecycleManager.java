package com.redhat.cloud.notifications;

import org.mockserver.integration.ClientAndServer;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

public class MockServerLifecycleManager {

    private static final String LOG_LEVEL_KEY = "mockserver.logLevel";

    private static ClientAndServer mockServer;
    private static String mockServerUrl;

    public static void start() {
        if (System.getProperty(LOG_LEVEL_KEY) == null) {
            System.setProperty(LOG_LEVEL_KEY, "OFF");
            System.out.println("MockServer log is disabled. Use '-D" + LOG_LEVEL_KEY + "=WARN|INFO|DEBUG|TRACE' to enable it.");
        }
        mockServer = startClientAndServer();
        mockServerUrl = "http://localhost:" + mockServer.getPort();
    }

    public static String getMockServerUrl() {
        return mockServerUrl;
    }

    public static ClientAndServer getClient() {
        return mockServer;
    }

    public static void stop() {
        mockServer.stop();
    }
}
