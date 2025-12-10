package com.redhat.cloud.notifications.connector.google.chat.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import io.getunleash.UnleashContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class GoogleChatConnectorConfig extends HttpConnectorConfig {

    private String toggleUseBetaTemplatesEnabled;

    @PostConstruct
    void googleChatConnectorPostConstruct() {
        toggleUseBetaTemplatesEnabled = toggleRegistry.register("use-beta-templates", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        config.put(toggleUseBetaTemplatesEnabled, isUseBetaTemplatesEnabled(null, null, null, null));

        return config;
    }


    public boolean isUseBetaTemplatesEnabled(final String orgId, final String bundle, final String application, final String eventType) {
        if (unleashEnabled) {
            String bundleApplicationEventType = null;
            if (null != bundle) {
                bundleApplicationEventType = bundle;
                if (null != application) {
                    bundleApplicationEventType += "#" + application;
                    if (null != eventType) {
                        bundleApplicationEventType += "#" + eventType;
                    }
                }
            }
            UnleashContext.Builder unleashContextBuilder = UnleashContext.builder()
                .addProperty("orgId", orgId);

            if (null != bundleApplicationEventType) {
                unleashContextBuilder.addProperty("bundleApplicationEventType", bundleApplicationEventType);
            }
            return unleash.isEnabled(toggleUseBetaTemplatesEnabled, unleashContextBuilder.build(), false);
        } else {
            return false;
        }
    }
}
