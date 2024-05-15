package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import io.getunleash.UnleashContext;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class EmailConnectorConfig extends HttpConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String BOP_URL = "notifications.connector.user-provider.bop.url";
    private static final String MAX_RECIPIENTS_PER_EMAIL = "notifications.connector.max-recipients-per-email";
    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";
    private static final String NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED = "notifications.emails-internal-only.enabled";
    private static final String NOTIFICATIONS_EMAILS_USE_BOP_V2_ENABLED = "notifications.emails-use-bop-v2.enabled";

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = BOP_URL)
    String bopURL;

    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @ConfigProperty(name = MAX_RECIPIENTS_PER_EMAIL, defaultValue = "50")
    int maxRecipientsPerEmail;

    @ConfigProperty(name = NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, defaultValue = "false")
    boolean emailsInternalOnlyEnabled;

    private String enableBopEmailServiceV2Toggle;

    @PostConstruct
    void emailConnectorPostConstruct() {
        enableBopEmailServiceV2Toggle = toggleRegistry.register("enable-bop-service-v2", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        config.put(BOP_ENV, bopEnv);
        config.put(BOP_URL, bopURL);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        config.put(MAX_RECIPIENTS_PER_EMAIL, maxRecipientsPerEmail);
        config.put(NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, emailsInternalOnlyEnabled);
        config.put(enableBopEmailServiceV2Toggle + " for all orgIds", isEnableBopServiceV2Usage(null));

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

    public String getBopURL() {
        return this.bopURL;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
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

    public boolean isEnableBopServiceV2Usage(String orgId) {
        UnleashContext unleashContext = UnleashContext.builder()
            .addProperty("orgId", orgId)
            .build();
        return unleash.isEnabled(enableBopEmailServiceV2Toggle, unleashContext, false);
    }

    /**
     * This method throws an {@link IllegalStateException} if it is invoked with a launch mode different from
     * {@link io.quarkus.runtime.LaunchMode#TEST TEST}. It should be added to methods that allow overriding a
     * config value from tests only, preventing doing so from runtime code.
     */
    private static void checkTestLaunchMode() {
        if (ProfileManager.getLaunchMode() != TEST) {
            throw new IllegalStateException("Illegal config value override detected");
        }
    }
}
