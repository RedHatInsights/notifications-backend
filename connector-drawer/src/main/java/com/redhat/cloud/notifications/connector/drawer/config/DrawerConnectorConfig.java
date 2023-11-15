package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
/*
 * @Alternative and Priority will soon go away.
 * See https://github.com/quarkusio/quarkus/issues/37042 for more details about the replacement.
 */
@Alternative
@Priority(0) // The value doesn't matter.
public class DrawerConnectorConfig extends HttpConnectorConfig {

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
