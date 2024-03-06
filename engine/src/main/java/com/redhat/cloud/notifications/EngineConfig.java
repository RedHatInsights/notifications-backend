package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import io.getunleash.Unleash;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class EngineConfig {

    // TODO Remove this when we're fully migrated to Unleash in all environments.
    @ConfigProperty(name = "notifications.unleash.enabled", defaultValue = "false")
    boolean unleashEnabled;

    @Inject
    Unleash unleash;

    // TODO Remove this when we're fully migrated to Unleash in all environments.
    @Inject
    FeatureFlipper featureFlipper;

    private static String toggleName(String feature) {
        return String.format("notifications-engine.%s.enabled", feature);
    }

    // Unleash toggles names.
    private static final String AGGREGATION_WITH_RECIPIENTS_RESOLVER_ENABLED = toggleName("aggregation-with-recipients-resolver");
    private static final String ASYNC_AGGREGATION_ENABLED = toggleName("async-aggregation");
    private static final String DEFAULT_TEMPLATE_ENABLED = toggleName("default-template");
    private static final String DRAWER_ENABLED = toggleName("drawer");
    private static final String DRAWER_CONNECTOR_ENABLED = toggleName("drawer-connector");
    private static final String EMAILS_ONLY_MODE_ENABLED = toggleName("emails-only-mode");
    private static final String HCC_EMAIL_SENDER_NAME_ENABLED = toggleName("hcc-email-sender-name");
    private static final String KAFKA_CONSUMED_TOTAL_CHECKER_ENABLED = toggleName("kafka-consumed-total-checker");
    private static final String SECURED_EMAIL_TEMPLATES_ENABLED = toggleName("secured-email-templates");

    private final Map<String, Supplier<Boolean>> loggedToggles = Map.of(
        AGGREGATION_WITH_RECIPIENTS_RESOLVER_ENABLED, this::isAggregationWithRecipientsResolverEnabled,
        ASYNC_AGGREGATION_ENABLED, this::isAsyncAggregationEnabled,
        DEFAULT_TEMPLATE_ENABLED, this::isDefaultTemplateEnabled,
        DRAWER_ENABLED, this::isDrawerEnabled,
        DRAWER_CONNECTOR_ENABLED, this::isDrawerConnectorEnabled,
        EMAILS_ONLY_MODE_ENABLED, this::isEmailsOnlyModeEnabled,
        HCC_EMAIL_SENDER_NAME_ENABLED, this::isHccEmailSenderNameEnabled,
        KAFKA_CONSUMED_TOTAL_CHECKER_ENABLED, this::isKafkaConsumedTotalCheckerEnabled,
        SECURED_EMAIL_TEMPLATES_ENABLED, this::isSecuredEmailTemplatesEnabled
    );

    void logFeatureTogglesAtStartup(@Observes Startup event) {
        Log.info("=== Feature toggles startup status ===");
        loggedToggles.forEach((name, value) -> {
            Log.infof("%s=%s", name, value.get());
        });
    }

    public boolean isAggregationWithRecipientsResolverEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(AGGREGATION_WITH_RECIPIENTS_RESOLVER_ENABLED, false);
        } else {
            return featureFlipper.isUseRecipientsResolverClowdappForDailyDigestEnabled();
        }
    }

    public boolean isAsyncAggregationEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(ASYNC_AGGREGATION_ENABLED, false);
        } else {
            return featureFlipper.isAsyncAggregation();
        }
    }

    public boolean isDefaultTemplateEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(DEFAULT_TEMPLATE_ENABLED, false);
        } else {
            return featureFlipper.isUseDefaultTemplate();
        }
    }

    public boolean isDrawerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(DRAWER_ENABLED, false);
        } else {
            return featureFlipper.isDrawerEnabled();
        }
    }

    public boolean isDrawerConnectorEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(DRAWER_CONNECTOR_ENABLED, false);
        } else {
            return featureFlipper.isDrawerConnectorEnabled();
        }
    }

    public boolean isEmailsOnlyModeEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(EMAILS_ONLY_MODE_ENABLED, true);
        } else {
            return featureFlipper.isEmailsOnlyMode();
        }
    }

    public boolean isHccEmailSenderNameEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(HCC_EMAIL_SENDER_NAME_ENABLED, false);
        } else {
            return featureFlipper.isHccEmailSenderNameEnabled();
        }
    }

    public boolean isKafkaConsumedTotalCheckerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(KAFKA_CONSUMED_TOTAL_CHECKER_ENABLED, false);
        } else {
            return featureFlipper.isKafkaConsumedTotalCheckerEnabled();
        }
    }

    public boolean isSecuredEmailTemplatesEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(SECURED_EMAIL_TEMPLATES_ENABLED, true);
        } else {
            return featureFlipper.isUseSecuredEmailTemplates();
        }
    }
}
