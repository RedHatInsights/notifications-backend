package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.v2.http.HttpConnectorConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
public class DrawerConnectorConfig extends HttpConnectorConfig {

    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";

    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    private String toggleUseCommonTemplateModule;
    private String togglePushNotificationsToKafka;

    @PostConstruct
    void drawerConnectorPostConstruct() {
        toggleUseCommonTemplateModule = toggleRegistry.register("use-common-template-module", true);
        togglePushNotificationsToKafka = toggleRegistry.register("push-notifications-to-kafka", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        config.put(toggleUseCommonTemplateModule, useCommonTemplateModule());
        config.put(togglePushNotificationsToKafka, pushNotificationsToKafka());
        return config;
    }

    public boolean useCommonTemplateModule() {
        return unleash.isEnabled(toggleUseCommonTemplateModule, false);
    }

    public boolean pushNotificationsToKafka() {
        return unleash.isEnabled(togglePushNotificationsToKafka, true);
    }

}
