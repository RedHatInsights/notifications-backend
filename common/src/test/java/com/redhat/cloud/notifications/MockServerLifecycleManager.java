package com.redhat.cloud.notifications;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class MockServerLifecycleManager {

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;
    private static String mockServerHttpsUrl;

    public static void start() {
        WireMockConfiguration config = WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath("wiremock/notifications-wiremock.jks")
            .keystorePassword("password")
            .keyManagerPassword("password");

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        // Configure WireMock client to use the server
        WireMock.configureFor("localhost", wireMockServer.port());

        // Set up SSL trust for HTTPS connections - only trust localhost and our custom WireMock test certificate
        try {
            TrustManager[] localhostTrustManagers = new TrustManager[] {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    // We don't accept client certificates in test mock server
                    throw new CertificateException("Client certificates not accepted in test mock server");
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                    if (certs == null || certs.length == 0) {
                        throw new CertificateException("No certificates provided");
                    }
                    // Only accept certificates with localhost or our custom WireMock test certificate
                    X509Certificate cert = certs[0];
                    String dn = cert.getSubjectX500Principal().getName();

                    // Check if it's a localhost certificate or our custom Notifications WireMock test certificate
                    boolean isValidTestCert = dn.contains("CN=localhost") ||
                                             dn.contains("CN=127.0.0.1") ||
                                             dn.contains("CN=::1") ||
                                             dn.contains("CN=Notifications WireMock cert");

                    if (!isValidTestCert) {
                        throw new CertificateException("Certificate not issued for localhost or Notifications WireMock. DN: " + dn);
                    }
                }
            } };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, localhostTrustManagers, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // Also set hostname verifier to only accept localhost
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return "localhost".equals(hostname) ||
                           "127.0.0.1".equals(hostname) ||
                           "::1".equals(hostname);
                }
            });
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
