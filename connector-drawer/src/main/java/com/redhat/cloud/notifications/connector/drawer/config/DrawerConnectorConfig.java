package com.redhat.cloud.notifications.connector.drawer.config;

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
public class DrawerConnectorConfig extends ConnectorConfig {
    private static final String FETCH_USERS_RBAC_ENABLED = "notifications.connector.fetch.users.rbac.enabled";
    private static final String IT_ELEMENTS_PAGE = "notifications.connector.user-provider.it.elements-per-page";
    private static final String IT_KEYSTORE_LOCATION = "notifications.connector.user-provider.it.key-store-location";
    private static final String IT_KEYSTORE_PASSWORD = "notifications.connector.user-provider.it.key-store-password";
    private static final String IT_USER_SERVICE_URL = "notifications.connector.user-provider.it.url";
    private static final String RBAC_APPLICATION_KEY = "notifications.connector.user-provider.rbac.application-key";
    private static final String RBAC_ELEMENTS_PAGE = "notifications.connector.user-provider.rbac.elements-per-page";
    private static final String RBAC_URL = "notifications.connector.user-provider.rbac.url";

    // The following two keys are public in order to make it easier for
    // overriding them in the tests.
    public static final String RBAC_DEVELOPMENT_AUTHENTICATION_KEY = "notifications.connector.user-provider.rbac.development-authentication-key";
    public static final String RBAC_PSKS = "notifications.connector.user-provider.rbac.psks";

    @ConfigProperty(name = FETCH_USERS_RBAC_ENABLED, defaultValue = "true")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = IT_ELEMENTS_PAGE, defaultValue = "1000")
    Integer itElementsPerPage;

    @ConfigProperty(name = IT_KEYSTORE_LOCATION)
    String itKeyStoreLocation;

    @ConfigProperty(name = IT_KEYSTORE_PASSWORD)
    String itKeyStorePassword;

    @ConfigProperty(name = IT_USER_SERVICE_URL)
    String itUserServiceURL;

    @ConfigProperty(name = RBAC_APPLICATION_KEY, defaultValue = "notifications")
    String rbacApplicationKey;

    @ConfigProperty(name = RBAC_DEVELOPMENT_AUTHENTICATION_KEY)
    Optional<String> rbacDevelopmentAuthenticationKey;

    /**
     * Base64 encoded {@link DrawerConnectorConfig#rbacDevelopmentAuthenticationKey}
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

    @Override
    public void log() {
        final Map<String, Object> additionalEntries = new HashMap<>();

        additionalEntries.put(FETCH_USERS_RBAC_ENABLED, this.fetchUsersWithRBAC);
        additionalEntries.put(IT_ELEMENTS_PAGE, this.itElementsPerPage);
        additionalEntries.put(IT_KEYSTORE_LOCATION, this.itKeyStoreLocation);
        additionalEntries.put(IT_KEYSTORE_PASSWORD, this.itKeyStorePassword);
        additionalEntries.put(IT_USER_SERVICE_URL, this.itUserServiceURL);
        additionalEntries.put(RBAC_APPLICATION_KEY, this.rbacApplicationKey);
        additionalEntries.put(RBAC_ELEMENTS_PAGE, this.rbacElementsPerPage);
        additionalEntries.put(RBAC_URL, this.rbacURL);

        log(additionalEntries);
    }

    public boolean isFetchUsersWithRBAC() {
        return this.fetchUsersWithRBAC;
    }

    public int getItElementsPerPage() {
        return this.itElementsPerPage;
    }

    public String getItKeyStoreLocation() {
        return this.itKeyStoreLocation;
    }

    public String getItKeyStorePassword() {
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

}
