package com.redhat.cloud.notifications.connector.email.processors.bop.ssl;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * A NOOP Trust Manager which trusts all the certificates.
 */
public class BOPTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
