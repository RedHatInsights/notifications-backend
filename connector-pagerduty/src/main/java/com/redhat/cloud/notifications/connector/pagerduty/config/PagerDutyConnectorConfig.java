package com.redhat.cloud.notifications.connector.pagerduty.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;

@ApplicationScoped
public class PagerDutyConnectorConfig extends HttpConnectorConfig {
    private static final String PAGERDUTY_URL_KEY = "notifications.connector.pagerduty.url";
    private static final String PAGERDUTY_EVENT_V2_URL = "https://events.pagerduty.com/v2/enqueue";

    @ConfigProperty(name = PAGERDUTY_URL_KEY, defaultValue = PAGERDUTY_EVENT_V2_URL)
    String pagerDutyUrl;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        config.put(PAGERDUTY_URL_KEY, pagerDutyUrl);

        return config;
    }

    public String getPagerDutyUrl() {
        return pagerDutyUrl;
    }
}
