package com.redhat.cloud.notifications;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class MockServerLifecycleManager {

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;
    private static String mockServerHttpsUrl;

    public static final String WIRE_MOCK_CERT_CN = "CN=Notifications WireMock cert";

    public static void start() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath("wiremock/notifications-wiremock.jks") // exp date: 2035-10-28
            .keystorePassword("password")
            .keyManagerPassword("password");

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        // Configure WireMock client to use the server
        WireMock.configureFor("localhost", wireMockServer.port());

        mockServerUrl = "http://localhost:" + wireMockServer.port();
        mockServerHttpsUrl = "https://localhost:" + wireMockServer.httpsPort();
        System.out.println("WireMock server started on HTTP port: " + wireMockServer.port() + " and HTTPS port: " + wireMockServer.httpsPort());
    }

    public static String getMockServerUrl() {
        return mockServerUrl;
    }

    public static String getMockServerHttpsUrl() {
        return mockServerHttpsUrl;
    }

    public static WireMockServer getClient() {
        return wireMockServer;
    }

    public static void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("WireMock server stopped");
        }
    }
}
