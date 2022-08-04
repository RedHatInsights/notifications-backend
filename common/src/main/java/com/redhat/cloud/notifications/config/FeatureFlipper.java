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

    @ConfigProperty(name = "ob.enabled", defaultValue = "false")
    boolean obEnabled;

    @ConfigProperty(name = "reinject.enabled", defaultValue = "false")
    boolean enableReInject;

    @ConfigProperty(name = "notifications.kafka-consumed-total-checker.enabled", defaultValue = "false")
    boolean kafkaConsumedTotalCheckerEnabled;

    @ConfigProperty(name = "notifications.use-org-id", defaultValue = "false")
    boolean useOrgId;

    @ConfigProperty(name = "notifications.events.use-org-id", defaultValue = "false")
    boolean useOrgIdInEvents;

    // TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
    @ConfigProperty(name = "notifications.translate-account-id-to-org-id", defaultValue = "false")
    boolean translateAccountIdToOrgId;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", FeatureFlipper.class.getSimpleName());
        Log.infof("The behavior groups unique name constraint is %s", enforceBehaviorGroupNameUnicity ? "enabled" : "disabled");
        Log.infof("The RHOSE (OpenBridge) integration is %s", obEnabled ? "enabled" : "disabled");
        Log.infof("The actions reinjection in case of Camel integration error is %s", enableReInject ? "enabled" : "disabled");
        Log.infof("The Kafka outage detector is %s", kafkaConsumedTotalCheckerEnabled ? "enabled" : "disabled");
        Log.infof("The org ID migration is %s", useOrgId ? "enabled" : "disabled");
        Log.infof("The org ID migration is %s in the events API", useOrgIdInEvents ? "enabled" : "disabled");
        Log.infof("The account ID translation to org ID is %s", translateAccountIdToOrgId ? "enabled" : "disabled");
    }

    public boolean isEnforceBehaviorGroupNameUnicity() {
        return enforceBehaviorGroupNameUnicity;
    }

    public boolean isObEnabled() {
        return obEnabled;
    }

    public void setObEnabled(boolean obEnabled) {
        checkTestLaunchMode();
        this.obEnabled = obEnabled;
    }

    public boolean isUseOrgId() {
        return useOrgId;
    }

    public void setUseOrgId(boolean useOrgId) {
        checkTestLaunchMode();
        this.useOrgId = useOrgId;
    }

    public boolean isUseOrgIdInEvents() {
        return useOrgIdInEvents;
    }

    public void setUseOrgIdInEvents(boolean useOrgIdInEvents) {
        checkTestLaunchMode();
        this.useOrgIdInEvents = useOrgIdInEvents;
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

    public boolean isTranslateAccountIdToOrgId() {
        return translateAccountIdToOrgId;
    }

    public void setTranslateAccountIdToOrgId(boolean translateAccountIdToOrgId) {
        checkTestLaunchMode();
        this.translateAccountIdToOrgId = translateAccountIdToOrgId;
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
