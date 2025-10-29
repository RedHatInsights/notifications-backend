package com.redhat.cloud.notifications.connector.http;

import io.quarkus.logging.Log;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Test-only override of HttpComponentConfigurator that configures HTTPS components
 * to trust localhost self-signed certificates and our custom WireMock test certificate.
 * This is necessary for testing with WireMock which uses self-signed certificates.
 *
 * SECURITY NOTE: This class is only used during tests and will NOT be included in production builds.
 * Only trusts certificates issued for localhost/127.0.0.1/::1 or our custom certificate (CN=Notifications WireMock cert).
 */
@Mock
@Dependent
public class TestHttpComponentConfigurator extends HttpComponentConfigurator {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    public void configure(CamelContext context) {
        // Call parent to configure basic HTTP settings
        super.configure(context);

        // Additionally configure SSL for HTTPS components in tests
        for (String httpComponent : connectorConfig.getHttpComponents()) {
            if ("https".equals(httpComponent)) {
                Log.infof("Test mode: Configuring HTTPS component to trust localhost certificates for WireMock testing");
                HttpComponent component = context.getComponent(httpComponent, HttpComponent.class);
                configureLocalhostSSL(component);
            }
        }
    }

    private void configureLocalhostSSL(HttpComponent component) {
        try {
            SSLContextParameters sslContextParameters = new SSLContextParameters();
            TrustManagersParameters trustManagersParameters = new TrustManagersParameters();

            // Trust localhost self-signed certificates only - for testing with WireMock
            trustManagersParameters.setTrustManager(new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // We don't accept client certificates in test mock server
                    throw new CertificateException("Client certificates not accepted in test mock server");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (chain == null || chain.length == 0) {
                        throw new CertificateException("No certificates provided");
                    }
                    // Only accept certificates with localhost or our custom WireMock test certificate
                    X509Certificate cert = chain[0];
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

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });

            sslContextParameters.setTrustManagers(trustManagersParameters);
            component.setSslContextParameters(sslContextParameters);

            // Only accept localhost hostnames for testing with self-signed certificates
            component.setX509HostnameVerifier((hostname, session) ->
                "localhost".equals(hostname) ||
                "127.0.0.1".equals(hostname) ||
                "::1".equals(hostname)
            );

            Log.debugf("Test mode: HTTPS component configured to trust localhost certificates only");
        } catch (Exception e) {
            Log.error("Failed to configure HTTPS component SSL for testing: " + e.getMessage(), e);
            throw new RuntimeException("SSL configuration failed in test mode", e);
        }
    }
}
