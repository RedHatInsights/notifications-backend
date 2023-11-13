package com.redhat.cloud.notifications.connector.http;

import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class SslTrustAllManager implements X509TrustManager {

    public static SSLContextParameters getSslContextParameters() {
        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setTrustManager(new SslTrustAllManager());
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(trustManagersParameters);
        return sslContextParameters;
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType) { }

    public void checkServerTrusted(X509Certificate[] chain, String authType) { }

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
