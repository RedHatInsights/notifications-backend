package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SplunkConnectorConfig extends ConnectorConfig {

    private static final String HTTPS_CONNECT_TIMEOUT_MS = "notifications.connector.https.connect-timeout-ms";
    private static final String HTTPS_SOCKET_TIMEOUT_MS = "notifications.connector.https.socket-timeout-ms";

    @ConfigProperty(name = HTTPS_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpsConnectTimeout;

    @ConfigProperty(name = HTTPS_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpsSocketTimeout;

    public void log() {
        super.log();
        Log.infof("%s=%s", HTTPS_CONNECT_TIMEOUT_MS, httpsConnectTimeout);
        Log.infof("%s=%s", HTTPS_SOCKET_TIMEOUT_MS, httpsSocketTimeout);
    }

    public int getHttpsConnectTimeout() {
        return httpsConnectTimeout;
    }

    public int getHttpsSocketTimeout() {
        return httpsSocketTimeout;
    }
}
