package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class BackendConfig {

    /*
     * Env vars configuration
     */
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";
    private static final String EMAILS_ONLY_MODE = "notifications.emails-only-mode.enabled";
    private static final String SECURED_EMAIL_TEMPLATES = "notifications.use-secured-email-templates.enabled";
    private static final String ERRATA_MIGRATION_BATCH_SIZE = "notifications.errata.migration.batch.size";
    private static final String INSTANT_EMAILS = "notifications.instant-emails.enabled";
    private static final String KESSEL_DOMAIN = "notifications.kessel.domain";
    private static final String KESSEL_ENABLED = "notifications.kessel.enabled";
    private static final String KESSEL_INSECURE_CLIENT_ENABLED = "notifications.kessel.insecure-client.enabled";
    private static final String KESSEL_TIMEOUT_MS = "notifications.kessel.timeout-ms";
    private static final String KESSEL_URL = "notifications.kessel.url";
    private static final String OIDC_CLIENT_ID = "notifications.oidc.client-id";
    private static final String OIDC_ISSUER = "notifications.oidc.issuer";
    private static final String OIDC_SECRET = "notifications.oidc.secret";
    private static final String RBAC_URL = "notifications.rbac.url";
    private static final String RBAC_ENABLED = "rbac.enabled";
    private static final String UNLEASH = "notifications.unleash.enabled";
    private static final String MAINTENANCE_MODE = "notifications.maintenance.mode";

    /*
     * Unleash configuration
     */
    private String drawerToggle;
    private String kesselToggle;
    private String kesselChecksOnEventLogToggle;
    private String maintenanceModeToggle;
    private String bypassBehaviorGroupMaxCreationLimitToggle;
    private String ignoreSourcesErrorOnEndpointDeleteToggle;
    private String useCommonTemplateModuleForUserPrefApisToggle;
    private String sourcesOidcAuthToggle;
    private String toggleUseBetaTemplatesEnabled;
    private String showHiddenEventTypesToggle;

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.drawer.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean drawerEnabled;

    // Only used in stage environments.
    @ConfigProperty(name = DEFAULT_TEMPLATE, defaultValue = "false")
    boolean defaultTemplateEnabled;

    // Only used in special environments.
    @ConfigProperty(name = EMAILS_ONLY_MODE, defaultValue = "false")
    boolean emailsOnlyModeEnabled;

    // Only used in special environments.
    @ConfigProperty(name = INSTANT_EMAILS, defaultValue = "false")
    boolean instantEmailsEnabled;

    @ConfigProperty(name = KESSEL_DOMAIN, defaultValue = "redhat")
    String kesselDomain;

    @ConfigProperty(name = KESSEL_ENABLED, defaultValue = "false")
    boolean kesselEnabled;

    @ConfigProperty(name = KESSEL_INSECURE_CLIENT_ENABLED, defaultValue = "false")
    boolean kesselInsecureClientEnabled;

    @ConfigProperty(name = KESSEL_TIMEOUT_MS, defaultValue = "30000")
    long kesselTimeoutMs;

    @ConfigProperty(name = KESSEL_URL)
    String kesselUrl;

    @ConfigProperty(name = OIDC_CLIENT_ID)
    String oidcClientId;

    @ConfigProperty(name = OIDC_ISSUER, defaultValue = "http://localhost:8084/realms/redhat-external")
    String oidcIssuer;

    @ConfigProperty(name = OIDC_SECRET)
    String oidcSecret;

    @ConfigProperty(name = RBAC_URL)
    String rbacUrl;

    @ConfigProperty(name = ERRATA_MIGRATION_BATCH_SIZE, defaultValue = "1000")
    int errataMigrationBatchSize;

    @ConfigProperty(name = RBAC_ENABLED, defaultValue = "true")
    protected boolean rbacEnabled;

    @ConfigProperty(name = MAINTENANCE_MODE, defaultValue = "false")
    boolean maintenanceModeEnabled;

    // Only used in special environments.
    @ConfigProperty(name = SECURED_EMAIL_TEMPLATES, defaultValue = "false")
    boolean useSecuredEmailTemplates;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        drawerToggle = toggleRegistry.register("drawer", true);
        kesselToggle = toggleRegistry.register("kessel", true);
        kesselChecksOnEventLogToggle = toggleRegistry.register("kessel-checks-on-event-log", true);
        maintenanceModeToggle = toggleRegistry.register("notifications-maintenance-mode", true);
        bypassBehaviorGroupMaxCreationLimitToggle = toggleRegistry.register("bypass-behavior-group-max-creation-limit", true);
        ignoreSourcesErrorOnEndpointDeleteToggle = toggleRegistry.register("ignore-sources-error-on-endpoint-delete", true);
        useCommonTemplateModuleForUserPrefApisToggle = toggleRegistry.register("use-common-template-module-for-user-pref-apis", true);
        sourcesOidcAuthToggle = toggleRegistry.register("sources-oidc-auth", true);
        toggleUseBetaTemplatesEnabled = toggleRegistry.register("use-beta-templates", true);
        showHiddenEventTypesToggle = toggleRegistry.register("show-hidden-event-types", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(ERRATA_MIGRATION_BATCH_SIZE, getErrataMigrationBatchSize());
        config.put(KESSEL_DOMAIN, kesselDomain);
        config.put(KESSEL_ENABLED, isKesselEnabled(null));
        config.put(KESSEL_TIMEOUT_MS, getKesselTimeoutMs());
        config.put(KESSEL_URL, kesselUrl);
        config.put(OIDC_ISSUER, oidcIssuer);
        config.put(RBAC_URL, rbacUrl);
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
        config.put(RBAC_ENABLED, isRBACEnabled());
        config.put(SECURED_EMAIL_TEMPLATES, useSecuredEmailTemplates);
        config.put(UNLEASH, unleashEnabled);
        config.put(sourcesOidcAuthToggle, isSourcesOidcAuthEnabled(null));
        config.put(showHiddenEventTypesToggle, isShowHiddenEventTypes(null));

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isDefaultTemplateEnabled() {
        return defaultTemplateEnabled;
    }

    public boolean isDrawerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(drawerToggle, false);
        } else {
            return drawerEnabled;
        }
    }

    public boolean isEmailsOnlyModeEnabled() {
        return emailsOnlyModeEnabled;
    }

    public int getErrataMigrationBatchSize() {
        return this.errataMigrationBatchSize;
    }

    public boolean isInstantEmailsEnabled() {
        return instantEmailsEnabled;
    }

    public String getKesselDomain() {
        return kesselDomain;
    }

    public boolean isKesselEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselToggle, unleashContext, false);
        } else {
            return kesselEnabled;
        }
    }

    public boolean isKesselInsecureClientEnabled() {
        return kesselInsecureClientEnabled;
    }

    public long getKesselTimeoutMs() {
        return kesselTimeoutMs;
    }

    public String getKesselUrl() {
        return kesselUrl;
    }

    public String getOidcClientId() {
        return oidcClientId;
    }

    public String getOidcIssuer() {
        return oidcIssuer;
    }

    public String getOidcSecret() {
        return oidcSecret;
    }

    public String getRbacUrl() {
        return rbacUrl;
    }

    public boolean isIgnoreSourcesErrorOnEndpointDelete(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(ignoreSourcesErrorOnEndpointDeleteToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    public boolean isKesselChecksOnEventLogEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselChecksOnEventLogToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    public boolean isUseCommonTemplateModuleForUserPrefApisToggle() {
        if (unleashEnabled) {
            return unleash.isEnabled(useCommonTemplateModuleForUserPrefApisToggle, true);
        } else {
            return true;
        }
    }

    public boolean isRBACEnabled() {
        return this.rbacEnabled;
    }

    private static UnleashContext buildUnleashContextWithOrgId(String orgId) {
        UnleashContext unleashContext = UnleashContext.builder()
            .addProperty("orgId", orgId)
            .build();
        return unleashContext;
    }

    public boolean isMaintenanceModeEnabled(String path) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContext.builder()
                .addProperty("method_and_path", path)
                .build();
            return unleash.isEnabled(maintenanceModeToggle, unleashContext, false);
        }
        return maintenanceModeEnabled;
    }

    public boolean isUseBetaTemplatesEnabled(final String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContext.builder()
                .addProperty("orgId", orgId)
                .build();
            return unleash.isEnabled(toggleUseBetaTemplatesEnabled, unleashContext, false);
        } else {
            return false;
        }
    }

    /**
     * Checks whether the behavior group creation limit is disabled for the
     * given organization.
     * @param orgId the organization we want to check the limit for.
     * @return {@code true} for almost all the organizations. We might disable
     * this limit on a very specific organization from time to time if we want
     * to perform some kind of tests, but it is going to be an exception.
     * @deprecated for removal because once behavior groups go away this
     * configuration check will not make any sense anymore.
     */
    @Deprecated(forRemoval = true)
    public boolean isBehaviorGroupCreationLimitDisabledForOrgId(final String orgId) {
        if (unleashEnabled) {
            final UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);

            return unleash.isEnabled(bypassBehaviorGroupMaxCreationLimitToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    public boolean isSourcesOidcAuthEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(sourcesOidcAuthToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    public boolean isShowHiddenEventTypes(String orgId) {
        if (unleashEnabled) {
            return unleash.isEnabled(showHiddenEventTypesToggle, buildUnleashContextWithOrgId(orgId), false);
        } else {
            return false;
        }
    }
}
