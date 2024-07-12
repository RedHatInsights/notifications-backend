package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.connector.ConnectorConfig.BASE_CONFIG_PRIORITY;
import static org.jboss.logging.Logger.Level;

@ApplicationScoped
@DefaultBean
@Priority(BASE_CONFIG_PRIORITY + 1)
public class HttpConnectorConfig extends ConnectorConfig {

    /*
     * Env vars configuration
     */
    private static final String CLIENT_ERROR_LOG_LEVEL = "notifications.connector.http.client-error.log-level";
    private static final String COMPONENTS = "notifications.connector.http.components";
    private static final String CONNECT_TIMEOUT_MS = "notifications.connector.http.connect-timeout-ms";
    private static final String CONNECTIONS_PER_ROUTE = "notifications.connector.http.connections-per-route";
    private static final String MAX_TOTAL_CONNECTIONS = "notifications.connector.http.max-total-connections";
    private static final String SERVER_ERROR_LOG_LEVEL = "notifications.connector.http.server-error.log-level";
    private static final String SOCKET_TIMEOUT_MS = "notifications.connector.http.socket-timeout-ms";

    @ConfigProperty(name = CLIENT_ERROR_LOG_LEVEL, defaultValue = "DEBUG")
    Level clientErrorLogLevel;

    @ConfigProperty(name = COMPONENTS, defaultValue = "http,https")
    List<String> httpComponents;

    @ConfigProperty(name = CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpConnectTimeout;

    @ConfigProperty(name = CONNECTIONS_PER_ROUTE, defaultValue = "20")
    int httpConnectionsPerRoute;

    @ConfigProperty(name = MAX_TOTAL_CONNECTIONS, defaultValue = "200")
    int httpMaxTotalConnections;

    @ConfigProperty(name = SERVER_ERROR_LOG_LEVEL, defaultValue = "DEBUG")
    Level serverErrorLogLevel;

    @ConfigProperty(name = SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpSocketTimeout;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(CLIENT_ERROR_LOG_LEVEL, clientErrorLogLevel);
        config.put(COMPONENTS, httpComponents);
        config.put(CONNECT_TIMEOUT_MS, httpConnectTimeout);
        config.put(CONNECTIONS_PER_ROUTE, httpConnectionsPerRoute);
        config.put(MAX_TOTAL_CONNECTIONS, httpMaxTotalConnections);
        config.put(SERVER_ERROR_LOG_LEVEL, serverErrorLogLevel);
        config.put(SOCKET_TIMEOUT_MS, httpSocketTimeout);
        return config;
    }

    public Level getClientErrorLogLevel() {
        return clientErrorLogLevel;
    }

    public List<String> getHttpComponents() {
        return httpComponents;
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

    public Level getServerErrorLogLevel() {
        return serverErrorLogLevel;
    }

    public int getHttpSocketTimeout() {
        return httpSocketTimeout;
    }
}
