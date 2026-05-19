package com.redhat.cloud.notifications.connector.v2;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.redhat.cloud.notifications.connector.v2.ConnectorConfig.BASE_CONFIG_PRIORITY;

@ApplicationScoped
@DefaultBean
@Priority(BASE_CONFIG_PRIORITY)
public class ConnectorConfig {

    public static final int BASE_CONFIG_PRIORITY = 0;

    private static final String NAME = "notifications.connector.name";
    private static final String SUPPORTED_CONNECTOR_HEADERS = "notifications.connector.supported-connector-headers";

    /*
     * Unleash configuration
     */

    private String sourcesHccClusterToggle;

    @ConfigProperty(name = NAME)
    String connectorName;

    @ConfigProperty(name = SUPPORTED_CONNECTOR_HEADERS)
    List<String> supportedConnectorHeaders;

    @Inject
    protected Unleash unleash;

    @Inject
    protected ToggleRegistry toggleRegistry;

    @PostConstruct
    void postConstruct() {
        sourcesHccClusterToggle = toggleRegistry.register("sources-hcc-cluster", true);
    }

    public void log() {
        Log.info("=== Startup configuration ===");
        getLoggedConfiguration().forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = new TreeMap<>();
        config.put(NAME, connectorName);
        config.put(SUPPORTED_CONNECTOR_HEADERS, supportedConnectorHeaders);
        config.put(sourcesHccClusterToggle, isSourcesHccClusterEnabled(null));
        return config;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public List<String> getSupportedConnectorHeaders() {
        return supportedConnectorHeaders;
    }

    public boolean isSourcesHccClusterEnabled(String orgId) {
        UnleashContext unleashContext = UnleashContextBuilder.buildUnleashContextWithOrgId(orgId);
        return unleash.isEnabled(sourcesHccClusterToggle, unleashContext, false);
    }
}
