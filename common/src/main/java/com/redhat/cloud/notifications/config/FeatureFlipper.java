package com.redhat.cloud.notifications.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

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

    @ConfigProperty(name = "notifications.use-templates-from-db", defaultValue = "false")
    boolean useTemplatesFromDb;

    @ConfigProperty(name = "notifications.disable-webhook-endpoints-on-failure", defaultValue = "false")
    boolean disableWebhookEndpointsOnFailure;

    @ConfigProperty(name = "notifications.use-sources-secrets-backend", defaultValue = "false")
    boolean sourcesSecretsBackend;

    @ConfigProperty(name = "notifications.use-rbac-for-fetching-users", defaultValue = "false")
    boolean useRbacForFetchingUsers;

    @ConfigProperty(name = "notifications.use-policies-email-templates-v2.enabled", defaultValue = "false")
    boolean policiesEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-compliance-email-templates-v2.enabled", defaultValue = "false")
    boolean complianceEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-ansible-email-templates-v2.enabled", defaultValue = "false")
    boolean ansibleEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-cost-management-email-templates-v2.enabled", defaultValue = "false")
    boolean costManagementEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.emails-only-mode.enabled", defaultValue = "false")
    boolean emailsOnlyMode;

    @ConfigProperty(name = "notifications.use-patch-email-templates-v2.enabled", defaultValue = "false")
    boolean patchEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-integrations-email-templates-v2.enabled", defaultValue = "false")
    boolean integrationsEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-drift-email-templates-v2.enabled", defaultValue = "false")
    boolean driftEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-sources-email-templates-v2.enabled", defaultValue = "false")
    boolean sourcesEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-inventory-email-templates-v2.enabled", defaultValue = "false")
    boolean inventoryEmailTemplatesV2Enabled;

    @ConfigProperty(name = "notifications.use-malware-email-templates-v2.enabled", defaultValue = "false")
    boolean malwareEmailTemplatesV2Enabled;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", FeatureFlipper.class.getSimpleName());
        Log.infof("The behavior groups unique name constraint is %s", enforceBehaviorGroupNameUnicity ? "enabled" : "disabled");
        Log.infof("The integrations unique name constraint is %s", enforceIntegrationNameUnicity ? "enabled" : "disabled");
        Log.infof("The actions reinjection in case of Camel integration error is %s", enableReInject ? "enabled" : "disabled");
        Log.infof("The Kafka outage detector is %s", kafkaConsumedTotalCheckerEnabled ? "enabled" : "disabled");
        Log.infof("The use of default templates is %s", useDefaultTemplate ? "enabled" : "disabled");
        Log.infof("The use of templates from database is %s", useTemplatesFromDb ? "enabled" : "disabled");
        Log.infof("The deactivation of webhook endpoints on failure is %s", disableWebhookEndpointsOnFailure ? "enabled" : "disabled");
        Log.infof("The sources back end as the secrets manager is %s", sourcesSecretsBackend ? "enabled" : "disabled");
        Log.infof("The use of rbac for fetching users is %s", useRbacForFetchingUsers ? "enabled" : "disabled");
        Log.infof("The Policies's email templates V2 are %s", policiesEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Compliance's email templates V2 are %s", complianceEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Ansible's email templates V2 are %s", ansibleEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Cost management's email templates V2 are %s", costManagementEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("Emails only mode is %s", emailsOnlyMode ? "enabled" : "disabled");
        Log.infof("The Patch's email templates V2 are %s", patchEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Integrations' email templates V2 are %s", integrationsEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Drift's email templates V2 are %s", driftEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Sources' email templates V2 are %s", sourcesEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Inventory's email templates V2 are %s", inventoryEmailTemplatesV2Enabled ? "enabled" : "disabled");
        Log.infof("The Malware's email templates V2 are %s", malwareEmailTemplatesV2Enabled ? "enabled" : "disabled");
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

    public boolean isUseTemplatesFromDb() {
        return useTemplatesFromDb;
    }

    public void setUseTemplatesFromDb(boolean useTemplatesFromDb) {
        checkTestLaunchMode();
        this.useTemplatesFromDb = useTemplatesFromDb;
    }

    public boolean isDisableWebhookEndpointsOnFailure() {
        return disableWebhookEndpointsOnFailure;
    }

    public void setDisableWebhookEndpointsOnFailure(boolean disableWebhookEndpointsOnFailure) {
        checkTestLaunchMode();
        this.disableWebhookEndpointsOnFailure = disableWebhookEndpointsOnFailure;
    }

    /**
     * Returns true if Sources is being used as the secrets backend to store the camel endpoints' and webhooks' basic
     * authentication and/or token's data.
     * @return true if the integration is enabled.
     */
    public boolean isSourcesUsedAsSecretsBackend() {
        return this.sourcesSecretsBackend;
    }

    public boolean isUseRbacForFetchingUsers() {
        return this.useRbacForFetchingUsers;
    }

    public void setUseRbacForFetchingUsers(boolean useRbacForFetchingUsers) {
        checkTestLaunchMode();
        this.useRbacForFetchingUsers = useRbacForFetchingUsers;
    }

    public boolean isPoliciesEmailTemplatesV2Enabled() {
        return this.policiesEmailTemplatesV2Enabled;
    }

    public boolean isComplianceEmailTemplatesV2Enabled() {
        return complianceEmailTemplatesV2Enabled;
    }

    public void setComplianceEmailTemplatesV2Enabled(boolean complianceEmailTemplatesV2Enabled) {
        this.complianceEmailTemplatesV2Enabled = complianceEmailTemplatesV2Enabled;
    }

    public boolean isAnsibleEmailTemplatesV2Enabled() {
        return ansibleEmailTemplatesV2Enabled;
    }

    public void setAnsibleEmailTemplatesV2Enabled(boolean ansibleEmailTemplatesV2Enabled) {
        this.ansibleEmailTemplatesV2Enabled = ansibleEmailTemplatesV2Enabled;
    }

    public boolean isCostManagementEmailTemplatesV2Enabled() {
        return costManagementEmailTemplatesV2Enabled;
    }

    public void setCostManagementEmailTemplatesV2Enabled(boolean costManagementEmailTemplatesV2Enabled) {
        this.costManagementEmailTemplatesV2Enabled = costManagementEmailTemplatesV2Enabled;
    }

    public boolean isEmailsOnlyMode() {
        return emailsOnlyMode;
    }

    public void setEmailsOnlyMode(boolean emailsOnlyMode) {
        checkTestLaunchMode();
        this.emailsOnlyMode = emailsOnlyMode;
    }

    public boolean isPatchEmailTemplatesV2Enabled() {
        return patchEmailTemplatesV2Enabled;
    }

    public void setPatchEmailTemplatesV2Enabled(boolean patchEmailTemplatesV2Enabled) {
        checkTestLaunchMode();
        this.patchEmailTemplatesV2Enabled = patchEmailTemplatesV2Enabled;
    }

    public boolean isIntegrationsEmailTemplatesV2Enabled() {
        return integrationsEmailTemplatesV2Enabled;
    }

    public void setIntegrationsEmailTemplatesV2Enabled(boolean integrationsEmailTemplatesV2Enabled) {
        checkTestLaunchMode();
        this.integrationsEmailTemplatesV2Enabled = integrationsEmailTemplatesV2Enabled;
    }

    public boolean isDriftEmailTemplatesV2Enabled() {
        return driftEmailTemplatesV2Enabled;
    }

    public void setDriftEmailTemplatesV2Enabled(boolean driftEmailTemplatesV2Enabled) {
        checkTestLaunchMode();
        this.driftEmailTemplatesV2Enabled = driftEmailTemplatesV2Enabled;
    }

    public boolean isSourcesEmailTemplatesV2Enabled() {
        return sourcesEmailTemplatesV2Enabled;
    }

    public void setSourcesEmailTemplatesV2Enabled(boolean sourcesEmailTemplatesV2Enabled) {
        checkTestLaunchMode();
        this.sourcesEmailTemplatesV2Enabled = sourcesEmailTemplatesV2Enabled;
    }

    public boolean isInventoryEmailTemplatesV2Enabled() {
        return inventoryEmailTemplatesV2Enabled;
    }

    public void setInventoryEmailTemplatesV2Enabled(boolean inventoryEmailTemplatesV2Enabled) {
        checkTestLaunchMode();
        this.inventoryEmailTemplatesV2Enabled = inventoryEmailTemplatesV2Enabled;
    }

    public boolean isMalwareEmailTemplatesV2Enabled() {
        return malwareEmailTemplatesV2Enabled;
    }

    public void setMalwareEmailTemplatesV2Enabled(boolean malwareEmailTemplatesV2Enabled) {
        this.malwareEmailTemplatesV2Enabled = malwareEmailTemplatesV2Enabled;
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
