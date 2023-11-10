package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.HashMap;
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
    public void log() {
        final Map<String, Object> additionalEntries = new HashMap<>();
        additionalEntries.put(DRAWER_TOPIC, outgoingDrawerTopic);
        additionalEntries.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        log(additionalEntries);
    }

    public String getOutgoingDrawerTopic() {
        return outgoingDrawerTopic;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
    }
}
