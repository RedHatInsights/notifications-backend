package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
public class DrawerConnectorConfig extends HttpConnectorConfig {

    private static final String DRAWER_TOPIC = "notifications.connector.kafka.outgoing.drawer.topic";

    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";
    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @ConfigProperty(name = DRAWER_TOPIC)
    String outgoingDrawerTopic;

    private String toggleUseSimplifiedRoute;

    @PostConstruct
    void emailConnectorPostConstruct() {
        toggleUseSimplifiedRoute = toggleRegistry.register("use-simplified-route", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(DRAWER_TOPIC, outgoingDrawerTopic);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        config.put(toggleUseSimplifiedRoute, useSimplifiedRoute(null));
        return config;
    }

    public String getOutgoingDrawerTopic() {
        return outgoingDrawerTopic;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
    }

    public boolean useSimplifiedRoute(String orgId) {
        if (unleashEnabled) {
            return unleash.isEnabled(toggleUseSimplifiedRoute, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), true);
        } else {
            return true;
        }
    }
}
