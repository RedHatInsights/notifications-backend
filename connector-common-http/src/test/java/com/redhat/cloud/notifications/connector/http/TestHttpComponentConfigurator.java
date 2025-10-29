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
import java.security.cert.X509Certificate;

/**
 * Test-only override of HttpComponentConfigurator that configures HTTPS components
 * to trust all SSL certificates and skip hostname verification.
 * This is necessary for testing with WireMock which uses self-signed certificates.
 *
 * SECURITY NOTE: This class is only used during tests and will NOT be included in production builds.
 * The production HttpComponentConfigurator does NOT trust all certificates.
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
                Log.infof("Test mode: Configuring HTTPS component to trust all certificates for WireMock testing");
                HttpComponent component = context.getComponent(httpComponent, HttpComponent.class);
                configureTrustAllSSL(component);
            }
        }
    }

    private void configureTrustAllSSL(HttpComponent component) {
        try {
            SSLContextParameters sslContextParameters = new SSLContextParameters();
            TrustManagersParameters trustManagersParameters = new TrustManagersParameters();

            // Trust all certificates - ONLY for testing with WireMock self-signed certs
            trustManagersParameters.setTrustManager(new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // Accept all client certificates in test mode
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // Accept all server certificates in test mode
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            });

            sslContextParameters.setTrustManagers(trustManagersParameters);
            component.setSslContextParameters(sslContextParameters);

            // Disable hostname verification for testing with self-signed certificates
            component.setX509HostnameVerifier(org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE);

            Log.debugf("Test mode: HTTPS component configured to trust all certificates");
        } catch (Exception e) {
            Log.error("Failed to configure HTTPS component SSL for testing: " + e.getMessage(), e);
            throw new RuntimeException("SSL configuration failed in test mode", e);
        }
    }
}
