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

    private static final String ENDPOINT_CACHE_MAX_SIZE = "notifications.connector.endpoint-cache-max-size";
    private static final String NAME = "notifications.connector.name";
    private static final String SUPPORTED_CONNECTOR_HEADERS = "notifications.connector.supported-connector-headers";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */

    /*
     * TODO: This sources-oidc-auth feature toggle is not ideally placed in the base ConnectorConfig class,
     * as it's only relevant for connectors that use Sources API authentication (PagerDuty, ServiceNow,
     * Splunk, and Webhook connectors). However, there's no better architectural solution currently available
     * that would allow sharing this toggle across multiple specific connector config classes without
     * duplicating the logic or creating complex inheritance hierarchies.
     *
     * This is a temporary situation - once PSK authentication is fully deprecated and removed from the
     * Sources API integration, this feature toggle can be removed entirely along with all PSK-related code.
     */
    private String sourcesOidcAuthToggle;

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    protected boolean unleashEnabled;

    @ConfigProperty(name = ENDPOINT_CACHE_MAX_SIZE, defaultValue = "100")
    int endpointCacheMaxSize;

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
        sourcesOidcAuthToggle = toggleRegistry.register("sources-oidc-auth", true);
    }

    public void log() {
        Log.info("=== Startup configuration ===");
        getLoggedConfiguration().forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = new TreeMap<>();
        config.put(ENDPOINT_CACHE_MAX_SIZE, endpointCacheMaxSize);
        config.put(NAME, connectorName);
        config.put(SUPPORTED_CONNECTOR_HEADERS, supportedConnectorHeaders);
        config.put(UNLEASH, unleashEnabled);
        config.put(sourcesOidcAuthToggle, isSourcesOidcAuthEnabled(null));
        return config;
    }

    public int getEndpointCacheMaxSize() {
        return endpointCacheMaxSize;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public List<String> getSupportedConnectorHeaders() {
        return supportedConnectorHeaders;
    }

    public boolean isSourcesOidcAuthEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContextBuilder.buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(sourcesOidcAuthToggle, unleashContext, false);
        } else {
            return false;
        }
    }
}
