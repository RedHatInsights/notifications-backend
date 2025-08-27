package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
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

    private String toggleUseCommonTemplateModule;

    @PostConstruct
    void emailConnectorPostConstruct() {
        toggleUseCommonTemplateModule = toggleRegistry.register("use-common-template-module", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(DRAWER_TOPIC, outgoingDrawerTopic);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        config.put(toggleUseCommonTemplateModule, useCommonTemplateModule());
        return config;
    }

    public boolean useCommonTemplateModule() {
        return unleash.isEnabled(toggleUseCommonTemplateModule, false);
    }

}
