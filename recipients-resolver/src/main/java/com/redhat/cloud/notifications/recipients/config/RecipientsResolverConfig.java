package com.redhat.cloud.notifications.recipients.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import io.getunleash.Unleash;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@ApplicationScoped
public class RecipientsResolverConfig {

    /*
     * Env vars configuration
     */
    private static final String MAX_RESULTS_PER_PAGE = "notifications.recipients-resolver.max-results-per-page";
    private static final String RETRY_INITIAL_BACKOFF = "notifications.recipients-resolver.retry.initial-backoff";
    private static final String RETRY_MAX_ATTEMPTS = "notifications.recipients-resolver.retry.max-attempts";
    private static final String RETRY_MAX_BACKOFF = "notifications.recipients-resolver.retry.max-backoff";
    private static final String WARN_IF_DURATION_EXCEEDS = "notifications.recipients-resolver.warn-if-request-duration-exceeds";
    public static final String MBOP_APITOKEN = "notifications.recipients-resolver.mbop.api_token";
    public static final String MBOP_CLIENT_ID = "notifications.recipients-resolver.mbop.client_id";
    private static final String MBOP_ENV = "notifications.recipients-resolver.mbop.env";
    private static final String KESSEL_URL = "notifications.kessel.url";
    private static final String KESSEL_TIMEOUT_MS = "notifications.kessel.timeout-ms";
    private static final String KESSEL_INSECURE_CLIENT_ENABLED = "notifications.kessel.insecure-client.enabled";
    private static final String KESSEL_CLIENT_ID = "notifications.kessel.authn.client-id";
    private static final String KESSEL_CLIENT_SECRET = "notifications.kessel.authn.client-secret";
    private static final String KESSEL_CLIENT_ISSUER = "notifications.kessel.authn.issuer";
    private static final String KESSEL_DOMAIN = "notifications.kessel.domain";

    /*
     * Unleash configuration
     */
    private String fetchUsersWithMbopToggle;
    private String fetchUsersWithRbacToggle;
    private String useKesselToggle;
    private String rbacOidcAuthToggle;

    @ConfigProperty(name = MAX_RESULTS_PER_PAGE, defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = RETRY_INITIAL_BACKOFF, defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = RETRY_MAX_ATTEMPTS, defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = RETRY_MAX_BACKOFF, defaultValue = "1S")
    Duration maxRetryBackoff;

    @ConfigProperty(name = WARN_IF_DURATION_EXCEEDS, defaultValue = "30S")
    Duration logTooLongRequestLimit;

    @ConfigProperty(name = MBOP_APITOKEN, defaultValue = "na")
    String mbopApiToken;

    @ConfigProperty(name = MBOP_CLIENT_ID, defaultValue = "na")
    String mbopClientId;

    @ConfigProperty(name = MBOP_ENV, defaultValue = "na")
    String mbopEnv;

    @ConfigProperty(name = KESSEL_URL, defaultValue = "localhost:9081")
    String kesselUrl;

    @ConfigProperty(name = KESSEL_TIMEOUT_MS, defaultValue = "30000")
    long kesselTimeoutMs;

    @ConfigProperty(name = KESSEL_CLIENT_ID)
    Optional<String> kesselClientId;

    @ConfigProperty(name = KESSEL_CLIENT_SECRET)
    Optional<String> kesselClientSecret;

    @ConfigProperty(name = KESSEL_CLIENT_ISSUER)
    Optional<String> kesselClientIssuer;

    /**
     * Is the gRPC client supposed to skip OAuth2 authentication and TLS verification?
     */
    @ConfigProperty(name = KESSEL_INSECURE_CLIENT_ENABLED, defaultValue = "false")
    boolean kesselInsecureClientEnabled;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    // Keystore path for certificate expiry logging (supports both JKS and PKCS12)
    // StartupUtils auto-detects the format from the file extension (.jks, .p12, .pfx)
    @ConfigProperty(name = "notifications.it-services.keystore-path")
    Optional<String> itServicesKeystorePath;

    // SECURITY WARNING: This property contains sensitive credentials - never log this value
    @ConfigProperty(name = "notifications.it-services.keystore-password")
    Optional<String> itServicesKeystorePassword;

    @ConfigProperty(name = KESSEL_DOMAIN, defaultValue = "redhat")
    String kesselDomain;

    @PostConstruct
    void postConstruct() {
        fetchUsersWithMbopToggle = toggleRegistry.register("fetch-users-with-mbop", true);
        fetchUsersWithRbacToggle = toggleRegistry.register("fetch-users-with-rbac", true);
        useKesselToggle = toggleRegistry.register("use-kessel", true);
        rbacOidcAuthToggle = toggleRegistry.register("rbac-oidc-auth", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(fetchUsersWithMbopToggle, isFetchUsersWithMbopEnabled(null));
        config.put(fetchUsersWithRbacToggle, isFetchUsersWithRbacEnabled(null));
        config.put(MAX_RESULTS_PER_PAGE, getMaxResultsPerPage());
        config.put(MBOP_ENV, getMbopEnv());
        config.put(RETRY_INITIAL_BACKOFF, getInitialRetryBackoff());
        config.put(RETRY_MAX_ATTEMPTS, getMaxRetryAttempts());
        config.put(RETRY_MAX_BACKOFF, getMaxRetryBackoff());
        config.put(WARN_IF_DURATION_EXCEEDS, getLogTooLongRequestLimit());
        config.put(useKesselToggle, isUseKesselEnabled(null));
        config.put(rbacOidcAuthToggle, isRbacOidcAuthEnabled(null));
        config.put(KESSEL_URL, getKesselUrl());
        config.put(KESSEL_TIMEOUT_MS, getKesselTimeoutMs());
        config.put(KESSEL_INSECURE_CLIENT_ENABLED, isKesselInsecureClientEnabled());
        config.put(KESSEL_DOMAIN, getKesselDomain());

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isFetchUsersWithMbopEnabled(String orgId) {
        return unleash.isEnabled(fetchUsersWithMbopToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
    }

    public boolean isFetchUsersWithRbacEnabled(String orgId) {
        return unleash.isEnabled(fetchUsersWithRbacToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
    }

    public boolean isUseKesselEnabled(String orgId) {
        return unleash.isEnabled(useKesselToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
    }

    public boolean isRbacOidcAuthEnabled(String orgId) {
        return unleash.isEnabled(rbacOidcAuthToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
    }

    public int getMaxResultsPerPage() {
        return maxResultsPerPage;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public Duration getInitialRetryBackoff() {
        return initialRetryBackoff;
    }

    public Duration getMaxRetryBackoff() {
        return maxRetryBackoff;
    }

    public String getMbopApiToken() {
        return mbopApiToken;
    }

    public String getMbopClientId() {
        return mbopClientId;
    }

    public String getMbopEnv() {
        return mbopEnv;
    }

    public boolean isKesselInsecureClientEnabled() {
        return kesselInsecureClientEnabled;
    }

    public String getKesselUrl() {
        return kesselUrl;
    }

    public long getKesselTimeoutMs() {
        return kesselTimeoutMs;
    }

    public Duration getLogTooLongRequestLimit() {
        return logTooLongRequestLimit;
    }

    public Optional<String> getItServicesKeystorePath() {
        return itServicesKeystorePath;
    }

    public Optional<String> getItServicesKeystorePassword() {
        return itServicesKeystorePassword;
    }

    public Optional<String> getKesselClientId() {
        return kesselClientId;
    }

    public Optional<String> getKesselClientSecret() {
        return kesselClientSecret;
    }

    public Optional<String> getKesselClientIssuer() {
        return kesselClientIssuer;
    }

    public String getKesselDomain() {
        return kesselDomain;
    }
}
