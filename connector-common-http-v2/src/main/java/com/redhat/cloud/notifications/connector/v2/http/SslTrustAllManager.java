package com.redhat.cloud.notifications.connector.v2.http;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class SslTrustAllManager implements X509TrustManager {

    public static SSLContext getSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new SslTrustAllManager()}, null);
        return sslContext;
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType) { }

    public void checkServerTrusted(X509Certificate[] chain, String authType) { }

    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
