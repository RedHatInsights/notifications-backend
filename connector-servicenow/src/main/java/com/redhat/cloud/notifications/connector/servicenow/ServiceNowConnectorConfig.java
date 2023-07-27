package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class ServiceNowConnectorConfig extends ConnectorConfig {

    private static final String HTTPS_CONNECT_TIMEOUT_MS = "notifications.connector.https.connect-timeout-ms";
    private static final String HTTPS_SOCKET_TIMEOUT_MS = "notifications.connector.https.socket-timeout-ms";

    @ConfigProperty(name = HTTPS_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpsConnectTimeout;

    @ConfigProperty(name = HTTPS_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpsSocketTimeout;

    @Override
    public void log() {
        Map<String, Object> additionalEntries = Map.of(
                HTTPS_CONNECT_TIMEOUT_MS, httpsConnectTimeout,
                HTTPS_SOCKET_TIMEOUT_MS, httpsSocketTimeout
        );
        log(additionalEntries);
    }

    public int getHttpsConnectTimeout() {
        return httpsConnectTimeout;
    }

    public int getHttpsSocketTimeout() {
        return httpsSocketTimeout;
    }
}
