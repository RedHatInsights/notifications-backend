package com.redhat.cloud.notifications.connector.pagerduty.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import io.getunleash.UnleashContext;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
public class PagerDutyConnectorConfig extends HttpConnectorConfig {
    private static final String PAGERDUTY_URL_KEY = "notifications.connector.pagerduty.url";
    private static final String PAGERDUTY_EVENT_V2_URL = "https://events.pagerduty.com/v2/enqueue";

    @ConfigProperty(name = PAGERDUTY_URL_KEY, defaultValue = PAGERDUTY_EVENT_V2_URL)
    String pagerDutyUrl;

    private String toggleDynamicPagerdutySeverity;

    @PostConstruct
    void pagerDutyConnectorPostConstruct() {
        toggleDynamicPagerdutySeverity = toggleRegistry.register("dynamic-pagerduty-severity", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        config.put(PAGERDUTY_URL_KEY, pagerDutyUrl);
        config.put(toggleDynamicPagerdutySeverity, isDynamicPagerdutySeverityEnabled(""));

        return config;
    }

    public String getPagerDutyUrl() {
        return pagerDutyUrl;
    }

    public boolean isDynamicPagerdutySeverityEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContextBuilder.buildUnleashContextWithOrgId(orgId);

            return unleash.isEnabled(toggleDynamicPagerdutySeverity, unleashContext, false);
        } else {
            return false;
        }
    }
}
