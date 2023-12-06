package com.redhat.cloud.notifications.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.quarkus.runtime.LaunchMode.TEST;

/**
 * <p>
 * This class centralizes all configuration values used to enable or disable a feature.
 * </p>
 * <p>
 * Any config value used to flip a temporary or permanent feature can be added that way:
 * <pre>
 * &#64;ApplicationScoped
 * public class FeatureFlipper {
 *
 *     &#64;ConfigProperty(name = "amazing-feature.enabled", defaultValue = "false")
 *     boolean amazingFeatureEnabled;
 *
 *     public boolean isAmazingFeatureEnabled() {
 *         return amazingFeatureEnabled;
 *     }
 *
 *     public void setAmazingFeatureEnabled(boolean amazingFeatureEnabled) {
 *         // Add this if the config value should only be overridden in TEST launch mode.
 *         checkTestLaunchMode();
 *         this.amazingFeatureEnabled = amazingFeatureEnabled;
 *     }
 * }
 * </pre>
 * </p>
 */
@ApplicationScoped
public class FeatureFlipper {

    @ConfigProperty(name = "notifications.enforce-bg-name-unicity", defaultValue = "false")
    boolean enforceBehaviorGroupNameUnicity;

    @ConfigProperty(name = "notifications.enforce-integration-name-unicity", defaultValue = "false")
    boolean enforceIntegrationNameUnicity;

    @ConfigProperty(name = "reinject.enabled", defaultValue = "false")
    boolean enableReInject;

    @ConfigProperty(name = "notifications.kafka-consumed-total-checker.enabled", defaultValue = "false")
    boolean kafkaConsumedTotalCheckerEnabled;

    @ConfigProperty(name = "notifications.use-default-template", defaultValue = "false")
    boolean useDefaultTemplate;

    @ConfigProperty(name = "notifications.disable-webhook-endpoints-on-failure", defaultValue = "false")
    boolean disableWebhookEndpointsOnFailure;

    @ConfigProperty(name = "notifications.use-sources-secrets-backend", defaultValue = "false")
    boolean sourcesSecretsBackend;

    @ConfigProperty(name = "notifications.use-rbac-for-fetching-users", defaultValue = "false")
    boolean useRbacForFetchingUsers;

    @ConfigProperty(name = "notifications.emails-only-mode.enabled", defaultValue = "false")
    boolean emailsOnlyMode;

    @ConfigProperty(name = "notifications.use-secured-email-templates.enabled", defaultValue = "false")
    boolean useSecuredEmailTemplates;

    @ConfigProperty(name = "notifications.instant-emails.enabled", defaultValue = "false")
    boolean instantEmailsEnabled;

    @ConfigProperty(name = "mp.messaging.incoming.exportrequests.enabled", defaultValue = "false")
    boolean exportServiceIntegrationEnabled;

    @ConfigProperty(name = "notifications.drawer.enabled", defaultValue = "false")
    boolean drawerEnabled;

    @ConfigProperty(name = "notifications.use-mbop-for-fetching-users", defaultValue = "false")
    boolean useMBOPForFetchingUsers;

    @ConfigProperty(name = "notifications.drawer-connector.enabled", defaultValue = "false")
    boolean drawerConnectorEnabled;

    @ConfigProperty(name = "notifications.async-aggregation.enabled", defaultValue = "true")
    boolean asyncAggregation;

    @ConfigProperty(name = "processor.email.aggregation.use-recipients-resolver-clowdapp.enabled", defaultValue = "false")
    boolean useRecipientsResolverClowdappForDailyDigestEnabled;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", FeatureFlipper.class.getSimpleName());
        Log.infof("The behavior groups unique name constraint is %s", enforceBehaviorGroupNameUnicity ? "enabled" : "disabled");
        Log.infof("The integrations unique name constraint is %s", enforceIntegrationNameUnicity ? "enabled" : "disabled");
        Log.infof("The actions reinjection in case of Camel integration error is %s", enableReInject ? "enabled" : "disabled");
        Log.infof("The Kafka outage detector is %s", kafkaConsumedTotalCheckerEnabled ? "enabled" : "disabled");
        Log.infof("The use of default templates is %s", useDefaultTemplate ? "enabled" : "disabled");
        Log.infof("The deactivation of webhook endpoints on failure is %s", disableWebhookEndpointsOnFailure ? "enabled" : "disabled");
        Log.infof("The sources back end as the secrets manager is %s", sourcesSecretsBackend ? "enabled" : "disabled");
        Log.infof("The use of rbac for fetching users is %s", useRbacForFetchingUsers ? "enabled" : "disabled");
        Log.infof("Emails only mode is %s", emailsOnlyMode ? "enabled" : "disabled");
        Log.infof("The use of secured email templates is %s", useSecuredEmailTemplates ? "enabled" : "disabled");
        Log.infof("Instant emails are %s", instantEmailsEnabled ? "enabled" : "disabled");
        Log.infof("The integration with the export service is %s", exportServiceIntegrationEnabled ? "enabled" : "disabled");
        Log.infof("Drawer feature is %s", drawerEnabled ? "enabled" : "disabled");
        Log.infof("The use of BOP/MBOP for fetching users is %s", useMBOPForFetchingUsers ? "enabled" : "disabled");
        Log.infof("The drawer connector is %s", drawerConnectorEnabled ? "enabled" : "disabled");
        Log.infof("The async aggregation is %s", asyncAggregation ? "enabled" : "disabled");
        Log.infof("The Recipients resolver usage for daily digest is %s", useRecipientsResolverClowdappForDailyDigestEnabled ? "enabled" : "disabled");
    }

    public boolean isEnforceBehaviorGroupNameUnicity() {
        return enforceBehaviorGroupNameUnicity;
    }

    public void setEnforceBehaviorGroupNameUnicity(boolean enforceBehaviorGroupNameUnicity) {
        checkTestLaunchMode();
        this.enforceBehaviorGroupNameUnicity = enforceBehaviorGroupNameUnicity;
    }

    public boolean isEnforceIntegrationNameUnicity() {
        return enforceIntegrationNameUnicity;
    }

    public void setEnforceIntegrationNameUnicity(boolean enforceIntegrationNameUnicity) {
        checkTestLaunchMode();
        this.enforceIntegrationNameUnicity = enforceIntegrationNameUnicity;
    }

    public boolean isEnableReInject() {
        return enableReInject;
    }

    public boolean isKafkaConsumedTotalCheckerEnabled() {
        return kafkaConsumedTotalCheckerEnabled;
    }

    public void setKafkaConsumedTotalCheckerEnabled(boolean kafkaConsumedTotalCheckerEnabled) {
        // It's ok to override this config value at runtime.
        this.kafkaConsumedTotalCheckerEnabled = kafkaConsumedTotalCheckerEnabled;
    }

    public boolean isUseDefaultTemplate() {
        return useDefaultTemplate;
    }

    public void setUseDefaultTemplate(boolean useDefaultTemplate) {
        checkTestLaunchMode();
        this.useDefaultTemplate = useDefaultTemplate;
    }

    public boolean isDisableWebhookEndpointsOnFailure() {
        return disableWebhookEndpointsOnFailure;
    }

    public void setDisableWebhookEndpointsOnFailure(boolean disableWebhookEndpointsOnFailure) {
        checkTestLaunchMode();
        this.disableWebhookEndpointsOnFailure = disableWebhookEndpointsOnFailure;
    }

    /**
     * Returns true if Sources is being used as the secrets backend to store
     * the camel endpoints' and webhooks' basic authentication and/or token's
     * data.
     * @return true if the integration is enabled.
     */
    public boolean isSourcesUsedAsSecretsBackend() {
        return this.sourcesSecretsBackend;
    }

    /**
     * Enables or disables using Sources as the secrets' backend.
     * @param sourcesSecretsBackend the on / off value for the feature.
     */
    public void setSourcesSecretsBackend(final boolean sourcesSecretsBackend) {
        checkTestLaunchMode();
        this.sourcesSecretsBackend = sourcesSecretsBackend;
    }

    public boolean isUseRbacForFetchingUsers() {
        return this.useRbacForFetchingUsers;
    }

    public void setUseRbacForFetchingUsers(boolean useRbacForFetchingUsers) {
        checkTestLaunchMode();
        this.useRbacForFetchingUsers = useRbacForFetchingUsers;
    }

    public boolean isEmailsOnlyMode() {
        return emailsOnlyMode;
    }

    public void setEmailsOnlyMode(boolean emailsOnlyMode) {
        checkTestLaunchMode();
        this.emailsOnlyMode = emailsOnlyMode;
    }

    public boolean isUseSecuredEmailTemplates() {
        return useSecuredEmailTemplates;
    }

    public void setUseSecuredEmailTemplates(boolean useSecuredEmailTemplates) {
        checkTestLaunchMode();
        this.useSecuredEmailTemplates = useSecuredEmailTemplates;
    }

    public boolean isInstantEmailsEnabled() {
        return instantEmailsEnabled;
    }

    public void setInstantEmailsEnabled(boolean instantEmailsEnabled) {
        checkTestLaunchMode();
        this.instantEmailsEnabled = instantEmailsEnabled;
    }

    public boolean isDrawerEnabled() {
        return drawerEnabled;
    }

    public void setDrawerEnabled(boolean drawerEnabled) {
        checkTestLaunchMode();
        this.drawerEnabled = drawerEnabled;
    }

    public boolean isUseMBOPForFetchingUsers() {
        return this.useMBOPForFetchingUsers;
    }

    public void setUseMBOPForFetchingUsers(final boolean useMBOPForFetchingUsers) {
        checkTestLaunchMode();
        this.useMBOPForFetchingUsers = useMBOPForFetchingUsers;
    }

    public boolean isDrawerConnectorEnabled() {
        return drawerConnectorEnabled;
    }

    public void setDrawerConnectorEnabled(boolean drawerConnectorEnabled) {
        checkTestLaunchMode();
        this.drawerConnectorEnabled = drawerConnectorEnabled;
    }

    public boolean isAsyncAggregation() {
        return asyncAggregation;
    }

    public void setAsyncAggregation(boolean asyncAggregation) {
        checkTestLaunchMode();
        this.asyncAggregation = asyncAggregation;
    }

    public boolean isUseRecipientsResolverClowdappForDailyDigestEnabled() {
        return useRecipientsResolverClowdappForDailyDigestEnabled;
    }

    public void setUseRecipientsResolverClowdappForDailyDigestEnabled(boolean useRecipientsResolverClowdappForDailyDigestEnabled) {
        checkTestLaunchMode();
        this.useRecipientsResolverClowdappForDailyDigestEnabled = useRecipientsResolverClowdappForDailyDigestEnabled;
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
