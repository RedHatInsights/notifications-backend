package com.redhat.cloud.notifications;

import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyStoreFactory;
import javax.net.ssl.HttpsURLConnection;

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
        // Thanks to the addition of MockServer KeyStoreFactory into default ssl context,
        // MockServer ssl certificate issuer will be recognized
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(Configuration.configuration(), new MockServerLogger()).sslContext().getSocketFactory());
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
        if (mockServer != null) {
            mockServer.stop();
        }
    }
}
