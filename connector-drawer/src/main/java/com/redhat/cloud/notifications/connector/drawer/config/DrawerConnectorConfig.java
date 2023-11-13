package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
public class DrawerConnectorConfig extends ConnectorConfig {

    private static final String DRAWER_TOPIC = "notifications.connector.kafka.outgoing.drawer.topic";

    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";
    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @ConfigProperty(name = DRAWER_TOPIC)
    String outgoingDrawerTopic;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(DRAWER_TOPIC, outgoingDrawerTopic);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        return config;
    }

    public String getOutgoingDrawerTopic() {
        return outgoingDrawerTopic;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
    }
}
