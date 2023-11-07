package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import io.quarkus.logging.Log;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class EmailConnectorConfig extends ConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String BOP_URL = "notifications.connector.user-provider.bop.url";
    private static final String FETCH_USERS_RBAC_ENABLED = "notifications.connector.fetch.users.rbac.enabled";
    private static final String IT_ELEMENTS_PAGE = "notifications.connector.user-provider.it.elements-per-page";
    private static final String IT_KEYSTORE_LOCATION = "notifications.connector.user-provider.it.key-store-location";
    private static final String IT_KEYSTORE_PASSWORD = "notifications.connector.user-provider.it.key-store-password";
    private static final String IT_USER_SERVICE_URL = "notifications.connector.user-provider.it.url";
    private static final String RBAC_APPLICATION_KEY = "notifications.connector.user-provider.rbac.application-key";
    private static final String RBAC_ELEMENTS_PAGE = "notifications.connector.user-provider.rbac.elements-per-page";
    private static final String RBAC_URL = "notifications.connector.user-provider.rbac.url";
    private static final String SKIP_BOP_USERS_RESOLUTION = "notifications.connector.bop.skip-users-resolution";
    @Deprecated(forRemoval = true)
    public static final String SINGLE_EMAIL_PER_USER = "notifications.connector.single-email-per-user.enabled";

    // The following two keys are public in order to make it easier for
    // overriding them in the tests.
    public static final String RBAC_DEVELOPMENT_AUTHENTICATION_KEY = "notifications.connector.user-provider.rbac.development-authentication-key";
    public static final String RBAC_PSKS = "notifications.connector.user-provider.rbac.psks";
    public static final String USER_PROVIDER_CACHE_EXPIRE_AFTER_WRITE = "notifications.connector.user-provider.cache.expire-after-write";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_MODULE_ENABLED = "notifications.connector.recipients-resolver.module.enabled";
    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_MODULE_ENABLED, defaultValue = "false")
    boolean recipientsResolverModuleEnabled;

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = BOP_URL)
    String bopURL;

    @ConfigProperty(name = SINGLE_EMAIL_PER_USER, defaultValue = "true")
    boolean singleEmailPerUserEnabled;

    @ConfigProperty(name = FETCH_USERS_RBAC_ENABLED, defaultValue = "true")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = IT_ELEMENTS_PAGE, defaultValue = "1000")
    Integer itElementsPerPage;

    @ConfigProperty(name = IT_KEYSTORE_LOCATION)
    Optional<String> itKeyStoreLocation;

    @ConfigProperty(name = IT_KEYSTORE_PASSWORD)
    Optional<String> itKeyStorePassword;

    @ConfigProperty(name = IT_USER_SERVICE_URL)
    String itUserServiceURL;

    @ConfigProperty(name = RBAC_APPLICATION_KEY, defaultValue = "notifications")
    String rbacApplicationKey;

    @ConfigProperty(name = RBAC_DEVELOPMENT_AUTHENTICATION_KEY)
    Optional<String> rbacDevelopmentAuthenticationKey;

    /**
     * Base64 encoded {@link EmailConnectorConfig#rbacDevelopmentAuthenticationKey}
     * value, used to bypass RBAC's authentication mechanism when developing,
     * in order to make development faster.
     */
    String rbacDevelopmentAuthenticationKeyAuthInfo;

    @ConfigProperty(name = RBAC_ELEMENTS_PAGE, defaultValue = "1000")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = RBAC_PSKS, defaultValue = "{}")
    String rbacPSKs;

    /**
     * Computed value combining the PSKs' JSON and the specified RBAC
     * application key.
     */
    String rbacPSK;

    @ConfigProperty(name = RBAC_URL)
    String rbacURL;

    @ConfigProperty(name = USER_PROVIDER_CACHE_EXPIRE_AFTER_WRITE, defaultValue = "600")
    int userProviderCacheExpireAfterWrite;

    @ConfigProperty(name = SKIP_BOP_USERS_RESOLUTION, defaultValue = "false")
    boolean skipBopUsersResolution;

    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @Override
    public void log() {
        final Map<String, Object> additionalEntries = new HashMap<>();

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        additionalEntries.put(BOP_ENV, this.bopEnv);
        additionalEntries.put(BOP_URL, this.bopURL);
        additionalEntries.put(FETCH_USERS_RBAC_ENABLED, this.fetchUsersWithRBAC);
        additionalEntries.put(IT_ELEMENTS_PAGE, this.itElementsPerPage);
        additionalEntries.put(IT_KEYSTORE_LOCATION, this.itKeyStoreLocation.orElse(""));
        additionalEntries.put(RBAC_APPLICATION_KEY, this.rbacApplicationKey);
        additionalEntries.put(RBAC_ELEMENTS_PAGE, this.rbacElementsPerPage);
        additionalEntries.put(RBAC_URL, this.rbacURL);
        additionalEntries.put(SINGLE_EMAIL_PER_USER, this.singleEmailPerUserEnabled);
        additionalEntries.put(USER_PROVIDER_CACHE_EXPIRE_AFTER_WRITE, this.userProviderCacheExpireAfterWrite);
        additionalEntries.put(SKIP_BOP_USERS_RESOLUTION, skipBopUsersResolution);
        additionalEntries.put(NOTIFICATIONS_RECIPIENTS_RESOLVER_MODULE_ENABLED, recipientsResolverModuleEnabled);
        additionalEntries.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        log(additionalEntries);
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

    public boolean isFetchUsersWithRBAC() {
        return this.fetchUsersWithRBAC;
    }

    public int getItElementsPerPage() {
        return this.itElementsPerPage;
    }

    public Optional<String> getItKeyStoreLocation() {
        return this.itKeyStoreLocation;
    }

    public Optional<String> getItKeyStorePassword() {
        return this.itKeyStorePassword;
    }

    public String getItUserServiceURL() {
        return this.itUserServiceURL;
    }

    public String getRbacApplicationKey() {
        return this.rbacApplicationKey;
    }

    public boolean isRbacDevelopmentAuthenticationKeyPresent() {
        return this.rbacDevelopmentAuthenticationKey.isPresent()
            && !this.rbacDevelopmentAuthenticationKey.get().isBlank();
    }

    public String getRbacDevelopmentAuthenticationKeyAuthInfo() {
        if (this.rbacDevelopmentAuthenticationKeyAuthInfo != null) {
            return this.rbacDevelopmentAuthenticationKeyAuthInfo;
        }

        // Base64 encode the authentication key if it is present.
        if (this.rbacDevelopmentAuthenticationKey.isPresent() && !this.rbacDevelopmentAuthenticationKey.get().isBlank()) {
            this.rbacDevelopmentAuthenticationKeyAuthInfo = new String(
                Base64.getEncoder().encode(this.rbacDevelopmentAuthenticationKey.get().getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8
            );
        }

        return this.rbacDevelopmentAuthenticationKeyAuthInfo;
    }

    public Integer getRbacElementsPerPage() {
        return this.rbacElementsPerPage;
    }

    public String getRbacPSK() {
        if (this.rbacPSK != null) {
            return this.rbacPSK;
        }

        final JsonObject rbacPSKs;
        try {
            rbacPSKs = new JsonObject(this.rbacPSKs);
        } catch (final DecodeException | NullPointerException e) {
            Log.errorf("Unable to load the RBAC PSKs from the environment variable", e);
            return "";
        }

        final JsonObject secret = rbacPSKs.getJsonObject(this.rbacApplicationKey);
        if (secret == null) {
            Log.errorf("Unable to find the \"notifications\" secret in the RBAC PSKs' JSON file");
            return "";
        }

        this.rbacPSK = secret.getString("secret");

        return this.rbacPSK;
    }

    public String getRbacURL() {
        return this.rbacURL;
    }

    public boolean isSingleEmailPerUserEnabled() {
        return this.singleEmailPerUserEnabled;
    }

    public int getUserProviderCacheExpireAfterWrite() {
        return userProviderCacheExpireAfterWrite;
    }

    public boolean isSkipBopUsersResolution() {
        return skipBopUsersResolution;
    }

    public void setSkipBopUsersResolution(boolean skipBopUsersResolution) {
        this.skipBopUsersResolution = skipBopUsersResolution;
    }

    public boolean isRecipientsResolverModuleEnabled() {
        return recipientsResolverModuleEnabled;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
    }
}
