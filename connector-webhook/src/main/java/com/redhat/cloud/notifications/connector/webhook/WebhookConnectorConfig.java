package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WebhookConnectorConfig extends ConnectorConfig {

    private static final String HTTPS_CONNECT_TIMEOUT_MS = "notifications.connector.https.connect-timeout-ms";
    private static final String HTTPS_SOCKET_TIMEOUT_MS = "notifications.connector.https.socket-timeout-ms";
    private static final String ALTERNATIVE_NAMES = "notifications.connector.alternative.names";

    @ConfigProperty(name = HTTPS_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpsConnectTimeout;

    @ConfigProperty(name = HTTPS_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpsSocketTimeout;

    @ConfigProperty(name = ALTERNATIVE_NAMES, defaultValue = "ansible")
    List<String> alternativeNames;

    @Override
    public void log() {
        Map<String, Object> additionalEntries = Map.of(
            HTTPS_CONNECT_TIMEOUT_MS, httpsConnectTimeout,
            HTTPS_SOCKET_TIMEOUT_MS, httpsSocketTimeout,
            ALTERNATIVE_NAMES, alternativeNames
        );
        log(additionalEntries);
    }

    public int getHttpsConnectTimeout() {
        return httpsConnectTimeout;
    }

    public int getHttpsSocketTimeout() {
        return httpsSocketTimeout;
    }

    public List<String> getAlternativeNames() {
        return alternativeNames;
    }
}
