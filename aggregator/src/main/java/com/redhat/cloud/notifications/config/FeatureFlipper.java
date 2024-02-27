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

    @ConfigProperty(name = "notifications.bundle.level.digest.enabled", defaultValue = "false")
    boolean singleDailyDigestEnabled;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", FeatureFlipper.class.getSimpleName());
        Log.infof("The daily digest at bundle level is %s", singleDailyDigestEnabled ? "enabled" : "disabled");

    }

    public boolean isSingleDailyDigestEnabled() {
        return singleDailyDigestEnabled;
    }

    public void setSingleDailyDigestEnabled(boolean singleDailyDigestEnabled) {
        checkTestLaunchMode();
        this.singleDailyDigestEnabled = singleDailyDigestEnabled;
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
