package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WebhookConnectorConfig extends ConnectorConfig {

    private static final String HTTP_CONNECT_TIMEOUT_MS = "notifications.connector.http.connect-timeout-ms";
    private static final String HTTP_CONNECTIONS_PER_ROUTE = "notifications.connector.http.connections-per-route";
    private static final String HTTP_MAX_TOTAL_CONNECTIONS = "notifications.connector.http.max-total-connections";
    private static final String HTTP_SOCKET_TIMEOUT_MS = "notifications.connector.http.socket-timeout-ms";
    private static final String ALTERNATIVE_NAMES = "notifications.connector.alternative.names";

    @ConfigProperty(name = HTTP_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpConnectTimeout;

    @ConfigProperty(name = HTTP_CONNECTIONS_PER_ROUTE, defaultValue = "20")
    int httpConnectionsPerRoute;

    @ConfigProperty(name = HTTP_MAX_TOTAL_CONNECTIONS, defaultValue = "200")
    int httpMaxTotalConnections;

    @ConfigProperty(name = HTTP_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpSocketTimeout;

    @ConfigProperty(name = ALTERNATIVE_NAMES, defaultValue = "ansible")
    List<String> alternativeNames;

    @Override
    public void log() {
        Map<String, Object> additionalEntries = Map.of(
                HTTP_CONNECT_TIMEOUT_MS, httpConnectTimeout,
                HTTP_CONNECTIONS_PER_ROUTE, httpConnectionsPerRoute,
                HTTP_MAX_TOTAL_CONNECTIONS, httpMaxTotalConnections,
                HTTP_SOCKET_TIMEOUT_MS, httpSocketTimeout,
                ALTERNATIVE_NAMES, alternativeNames
        );
        log(additionalEntries);
    }

    public int getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    public int getHttpConnectionsPerRoute() {
        return httpConnectionsPerRoute;
    }

    public int getHttpMaxTotalConnections() {
        return httpMaxTotalConnections;
    }

    public int getHttpSocketTimeout() {
        return httpSocketTimeout;
    }

    public List<String> getAlternativeNames() {
        return alternativeNames;
    }
}
