package com.redhat.cloud.notifications;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class MockServerLifecycleManager {

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;
    private static String mockServerHttpsUrl;

    public static void start() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort();

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        // Configure WireMock client to use the server
        WireMock.configureFor("localhost", wireMockServer.port());

        // Set up SSL trust for HTTPS connections - trust all certificates for testing
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            } };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            System.err.println("Failed to set up SSL context: " + e.getMessage());
        }

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
