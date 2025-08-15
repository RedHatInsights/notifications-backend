package com.redhat.cloud.notifications.recipients.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.project_kessel.clients.authn.AuthenticationConfig;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
    private static final String UNLEASH = "notifications.unleash.enabled";
    public static final String MBOP_APITOKEN = "notifications.recipients-resolver.mbop.api_token";
    public static final String MBOP_CLIENT_ID = "notifications.recipients-resolver.mbop.client_id";
    private static final String MBOP_ENV = "notifications.recipients-resolver.mbop.env";
    private static final String KESSEL_TARGET_URL = "notifications.recipients-resolver.kessel.target-url";
    private static final String KESSEL_USE_SECURE_CLIENT = "relations-api.is-secure-clients";
    private static final String KESSEL_CLIENT_ID = "relations-api.authn.client.id";
    private static final String KESSEL_CLIENT_SECRET = "relations-api.authn.client.secret";
    private static final String KESSEL_CLIENT_ISSUER = "relations-api.authn.client.issuer";
    private static final String KESSEL_CLIENT_MODE = "relations-api.authn.mode";
    private static final String KESSEL_DOMAIN = "notifications.kessel.domain";

    /*
     * Unleash configuration
     */
    private String fetchUsersWithMbopToggle;
    private String fetchUsersWithRbacToggle;
    private String useKesselToggle;
    private String rbacOidcAuthToggle;

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.rbac.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean fetchUsersWithRbacEnabled;

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.mbop.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean fetchUsersWithMbopEnabled;

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

    @ConfigProperty(name = KESSEL_TARGET_URL, defaultValue = "localhost:9000")
    String kesselTargetUrl;

    @ConfigProperty(name = KESSEL_CLIENT_ID)
    Optional<String> kesselClientId;

    @ConfigProperty(name = KESSEL_CLIENT_SECRET)
    Optional<String> kesselClientSecret;

    @ConfigProperty(name = KESSEL_CLIENT_ISSUER)
    Optional<String> kesselClientIssuer;

    @ConfigProperty(name = KESSEL_CLIENT_MODE)
    AuthenticationConfig.AuthMode kesselClientMode;

    /**
     * Is the gRPC client supposed to connect to a secure, HTTPS endpoint?
     */
    @ConfigProperty(name = KESSEL_USE_SECURE_CLIENT, defaultValue = "false")
    boolean kesselUseSecureClient;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @ConfigProperty(name = "quarkus.rest-client.it-s2s.key-store")
    Optional<URI> quarkusItServiceKeystore;

    @ConfigProperty(name = "quarkus.rest-client.it-s2s.key-store")
    Optional<String> quarkusItServiceKeystoreType;

    @ConfigProperty(name = "quarkus.rest-client.it-s2s.key-store-password")
    Optional<String> quarkusItServicePassword;

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
        config.put(UNLEASH, unleashEnabled);
        config.put(useKesselToggle, isUseKesselEnabled(null));
        config.put(rbacOidcAuthToggle, isRbacOidcAuthEnabled(null));
        config.put(KESSEL_TARGET_URL, getKesselTargetUrl());
        config.put(KESSEL_USE_SECURE_CLIENT, isKesselUseSecureClient());
        config.put(KESSEL_DOMAIN, getKesselDomain());

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isFetchUsersWithMbopEnabled(String orgId) {
        if (unleashEnabled) {
            return unleash.isEnabled(fetchUsersWithMbopToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
        } else {
            return fetchUsersWithMbopEnabled;
        }
    }

    public boolean isFetchUsersWithRbacEnabled(String orgId) {
        if (unleashEnabled) {
            return unleash.isEnabled(fetchUsersWithRbacToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
        } else {
            return fetchUsersWithRbacEnabled;
        }
    }

    public boolean isUseKesselEnabled(String orgId) {
        if (unleashEnabled) {
            return unleash.isEnabled(useKesselToggle, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
        } else {
            return false;
        }
    }

    public boolean isRbacOidcAuthEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContext.builder()
                .addProperty("orgId", orgId)
                .build();
            return unleash.isEnabled(rbacOidcAuthToggle, unleashContext, false);
        } else {
            return false;
        }
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

    public boolean isKesselUseSecureClient() {
        return kesselUseSecureClient;
    }

    public String getKesselTargetUrl() {
        try {
            final URL url = new URI(kesselTargetUrl).toURL();
            final String newKesselUrl = url.getHost() + ":9000";

            Log.debugf("Kessel URL changed from \"%s\" to \"%s\"", kesselTargetUrl, newKesselUrl);

            return newKesselUrl;
        } catch (final IllegalArgumentException | MalformedURLException | URISyntaxException e) {
            Log.debugf(e, "Unable to create a URL from value \"%s\"", kesselTargetUrl);
            return kesselTargetUrl;
        }
    }

    public Duration getLogTooLongRequestLimit() {
        return logTooLongRequestLimit;
    }

    public Optional<URI> getQuarkusItServiceKeystore() {
        return quarkusItServiceKeystore;
    }

    /** Maps from Quarkus-recognized "P12" to {@link java.security.KeyStore} type of "pkcs12"; otherwise returns as-is. */
    public Optional<String> getMappedQuarkusItServiceKeystoreType() {
        if (quarkusItServiceKeystoreType.isPresent() && quarkusItServiceKeystoreType.get().equalsIgnoreCase("P12")) {
            return Optional.of("pkcs12");
        } else {
            return quarkusItServiceKeystoreType;
        }
    }

    public Optional<String> getQuarkusItServicePassword() {
        return quarkusItServicePassword;
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

    public AuthenticationConfig.AuthMode getKesselClientMode() {
        return kesselClientMode;
    }

    public String getKesselDomain() {
        return kesselDomain;
    }
}
