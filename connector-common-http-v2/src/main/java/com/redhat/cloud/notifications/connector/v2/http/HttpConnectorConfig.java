package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

import static com.redhat.cloud.notifications.connector.v2.ConnectorConfig.BASE_CONFIG_PRIORITY;
import static org.jboss.logging.Logger.Level;

@ApplicationScoped
@DefaultBean
@Priority(BASE_CONFIG_PRIORITY + 1)
public class HttpConnectorConfig extends ConnectorConfig {

    /*
     * Env vars configuration
     */
    private static final String CLIENT_ERROR_LOG_LEVEL = "notifications.connector.http.client-error.log-level";
    private static final String SERVER_ERROR_LOG_LEVEL = "notifications.connector.http.server-error.log-level";

    @ConfigProperty(name = CLIENT_ERROR_LOG_LEVEL, defaultValue = "DEBUG")
    Level clientErrorLogLevel;

    @ConfigProperty(name = SERVER_ERROR_LOG_LEVEL, defaultValue = "DEBUG")
    Level serverErrorLogLevel;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(CLIENT_ERROR_LOG_LEVEL, clientErrorLogLevel);
        config.put(SERVER_ERROR_LOG_LEVEL, serverErrorLogLevel);
        return config;
    }

    public Level getClientErrorLogLevel() {
        return clientErrorLogLevel;
    }

    public Level getServerErrorLogLevel() {
        return serverErrorLogLevel;
    }
}
