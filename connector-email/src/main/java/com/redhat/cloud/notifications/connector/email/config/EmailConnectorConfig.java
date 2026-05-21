package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.v2.http.HttpConnectorConfig;
import io.getunleash.UnleashContext;
import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@ApplicationScoped
public class EmailConnectorConfig extends HttpConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String MAX_RECIPIENTS_PER_EMAIL = "notifications.connector.max-recipients-per-email";
    private static final String NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED = "notifications.emails-internal-only.enabled";

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = MAX_RECIPIENTS_PER_EMAIL, defaultValue = "50")
    int maxRecipientsPerEmail;

    @ConfigProperty(name = NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, defaultValue = "false")
    boolean emailsInternalOnlyEnabled;

    private String toggleKafkaIncomingHighVolumeTopic;
    private String toggleUseBetaTemplatesEnabled;

    @PostConstruct
    void emailConnectorPostConstruct() {
        toggleKafkaIncomingHighVolumeTopic = toggleRegistry.register("kafka-incoming-high-volume-topic", true);
        toggleUseBetaTemplatesEnabled = toggleRegistry.register("use-beta-templates", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        config.put(BOP_ENV, bopEnv);
        config.put(MAX_RECIPIENTS_PER_EMAIL, maxRecipientsPerEmail);
        config.put(NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, emailsInternalOnlyEnabled);
        config.put(toggleKafkaIncomingHighVolumeTopic, isIncomingKafkaHighVolumeTopicEnabled());
        config.put(toggleUseBetaTemplatesEnabled, isUseBetaTemplatesEnabled(null, null, null, null));
        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        return config;
    }

    public String getBopApiToken() {
        return this.bopApiToken;
    }

    public String getBopClientId() {
        return this.bopClientId;
    }

    public String getBopEnv() {
        return this.bopEnv;
    }

    public boolean isIncomingKafkaHighVolumeTopicEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(toggleKafkaIncomingHighVolumeTopic, false);
        } else {
            return false;
        }
    }

    public int getMaxRecipientsPerEmail() {
        return maxRecipientsPerEmail;
    }

    public boolean isEmailsInternalOnlyEnabled() {
        return emailsInternalOnlyEnabled;
    }

    public void setEmailsInternalOnlyEnabled(boolean emailsInternalOnlyEnabled) {
        checkTestLaunchMode();
        this.emailsInternalOnlyEnabled = emailsInternalOnlyEnabled;
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

    private static void checkTestLaunchMode() {
        if (!LaunchMode.current().isDevOrTest()) {
            throw new IllegalStateException("Illegal config value override detected");
        }
    }
}
